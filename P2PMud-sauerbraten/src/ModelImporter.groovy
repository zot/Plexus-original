import java.awt.FlowLayoutimport net.miginfocom.swing.MigLayout
import groovy.swing.SwingBuilder
import java.awt.Dimension
import javax.swing.JFileChooser
import p2pmud.MessageBox
import p2pmud.Unzip
import java.io.*;
import javax.imageio.*;
import java.awt.image.*;
import java.awt.Graphics;
import java.awt.Panel;
import javax.swing.ImageIcon
import javax.swing.SwingConstants

class ModelImporter {
	static void main(args) {
		showGui()
	}
	
	static 	def showGui() {
		def f, importField, exportField
		new SwingBuilder().build {
				f = frame(title: 'Plexus .md2/.md3 Importer', windowClosing: {System.exit(0)}, layout: new MigLayout('fillx'), pack: true, show: true) {
				
				label(text:"Please provide an import and export path", constraints:('span 2, wrap'))
				button(text: "Import...", actionPerformed: { fileImport(importField) })
				importField = textField(text: '/tmp/bobamd2', constraints: 'growx, wrap')
				button(text: "Export...", actionPerformed: { fileExport(exportField) })
				exportField = textField(text: '/tmp/bobasauer', constraints: 'growx, wrap')
				button(text: "Convert", actionPerformed: { convertModel(importField.text, exportField.text) }, constraints: ('wrap'))
				button(text: "Exit", actionPerformed: {f.dispose(); System.exit(0) })
			}
			f.size = [500, (int)f.size.height] as Dimension
		}
	}
	
	static def fileImport(impField) {
		def dir = impField.text
		def ch = new JFileChooser(dir);
		ch.setDialogTitle("Please specify the folder containing the model");
		ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
		if (ch.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
			File file=ch.getSelectedFile();
			dir = file.getAbsolutePath();
		} else {
			return
		}
		System.setProperty('importDir', dir as String)
		impField.text = dir
	}
	static def fileExport(expField) {
		def dir = expField.text
		def ch = new JFileChooser(dir);
		ch.setDialogTitle("Please specify the folder to export the converted model");
		ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
		if (ch.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
			File file=ch.getSelectedFile();
			dir = file.getAbsolutePath();
		} else {
			return
		}
		System.setProperty('exportDir', dir as String)
		expField.text = dir
	}
	static def convertModel(impPath, expPath) {
		def convertor = new ModelConvertor(impPath, expPath)
		
		convertor.convert()
	}
	
	
}


class ModelConvertor
{
	def imageMaps
	def pattern = ~".*(\\.png|\\.jpg)";
	def File srcDir, dstDir
	def title = "Plexus MD2 Convertor"
	
	def ModelConvertor(impPath, expPath) {
		srcDir = new File(impPath);
		dstDir = new File(expPath);
	}

	def convert() {
		if (!srcDir.isDirectory()) {
			MessageBox.Show(title, "Error: Import folder not found")
			return
		}
		if (!dstDir.isDirectory()) dstDir.mkdir()
		if (!dstDir.isDirectory()) {
			MessageBox.Show(title, "Error: Export folder could not be created")
			return
		}
		
		mogrify("pcx")
		mogrify("tga")
		mogrify("tif")
		mogrify("gif")
		
		def md2 = new File(srcDir, "tris.md2")
		if (md2.isFile()) return convertMd2(md2)
		def md3 = new File(srcDir, "animation.cfg")
		if (md3.isFile()) return convertMd3(md3)
		
		def packs = getPackages()
		if (packs) {
			println "Going to unpack packages"
			for (p in packs) Unzip.Unzipper(p, srcDir, false)
			md3 = new File(srcDir, "animation.cfg")
			if (md3.isFile()) return convertMd3(md3)
		}
		MessageBox.Show(title, "Error: Import folder doesn't contain an .md2 or .md3 model")  
	}
	
	def mogrify(ext) {
		def dir = srcDir.getAbsolutePath()
		def cmd = "mogrify -format png -type TrueColor -depth 16 $dir/*.$ext"
		//println "Mogrify Cmd: $cmd"
		def proc = Runtime.getRuntime().exec(cmd)
		proc.waitFor()
	}
	
	def convertMd3(File animcfg) {
		def lower = new File(srcDir, "lower_default.skin").readLines()
		def upper = new File(srcDir, "upper_default.skin").readLines()
		def head = new File(srcDir, "head_default.skin").readLines()

		def md3 = [], needCopy = [ 'animation.cfg' ]
		md3 << "// MD3 model converted by Plexus Convertor V1.0"
		copyMD3Section(md3, 'lower', lower, needCopy)
		
		// extract only the animation lines from animation.cfg
		def lines = animcfg.readLines(), anims = []
		for (line in lines) {
			if (line && Character.isDigit(line[0] as Character)) {
				//println line
				anims << line
			}
		}
		
		// build lower animations
		extractAnimation(md3, "dying", anims, 0, true)
		extractAnimation(md3, "dead", anims, 1, true)
		extractAnimation(md3, "lag|edit", anims, 13, true, true) // single frame
		extractAnimation(md3, "forward|left|right", anims, 14, true)
		extractAnimation(md3, "backward", anims, 16, true)
		extractAnimation(md3, "swim", anims, 17, true)
		extractAnimation(md3, "jump", anims, 18, true)
		extractAnimation(md3, "idle", anims, 22, true)
		
		copyMD3Section(md3, 'upper', upper, needCopy)
		
		// build uppper animations
		extractAnimation(md3, "dying", anims, 0, false)
		extractAnimation(md3, "dead", anims, 1, false)
		extractAnimation(md3, "idle|lag|edit", anims, 11, false, true) // single frame
		extractAnimation(md3, "shoot", anims, 7, false)
		extractAnimation(md3, "punch", anims, 8, false)
		//extractAnimation(md3, "pain", anims, 22, false)
		extractAnimation(md3, "taunt", anims, 6, false)
		
		copyMD3Section(md3, 'head', head, needCopy)
		
		md3 << "md3link 0 1 tag_torso"
		md3 << "md3link 1 2 tag_head"
		md3 << ""
		md3 << "mdlspec -1"
		md3 << "mdlscale 100"
		md3 << "mdltrans 0 0 24"
		
		def f = new File(dstDir, 'md3.cfg')
		f.withWriter{ writer ->
	       for (line in md3) writer << line << "\n"
		} 
		
		for (file in needCopy.unique()) copyFile(file)
		
		copyThumb('icon_default.png')
		
		MessageBox.Show("Plexus MD3 Convertor", "Success!")
	}
	def extractAnimation(md3, name, anims, index, legs) {
		extractAnimation(md3, name, anims, index, legs, false)
	}
	def extractAnimation(md3, name, anims, index, legs, single) {
		def first, length, speed
		def line = anims[index]
		def pattern = /(\d+)\s+(\d+)\s+(\d+)\s+(\d+)/
		def m = anims[index] =~ pattern
		first = Integer.parseInt(m[0][1])
		length = single ? 1 : m[0][2]
		speed = m[0][4]
		if (legs && first >= 60) first -= 60
		if (name.contains('|')) name = '"' + name + '"'
		def out = "md3anim $name $first $length $speed"
		println out
		md3 << out
	}

	def copyMD3Section(md3, section, lines, needCopy) {
		needCopy << "${section}.md3"
		md3 << ''
		md3 << ("md3load ${section}.md3")
		def pattern = /([^,]+),([^,]*)/
		for (line in lines) {
			def m = line =~ pattern
			def first = m[0][1]
			def second = m[0][2]
			//println "$first -> $second"
			if (second) {
				def image = new File(second).getName().toLowerCase()
				//println "first $image"
				if (image.contains('.png')  || image.contains('.jpg')) {
					// leave image alone
				} else {
					image = image.replaceFirst(/\.[a-z]+$/, '.png')
				}
				//println image
				if (new File(srcDir, image).isFile()) {
					md3 << ("md3skin $first $image")
					needCopy << (image)
				} else {
					MessageBox.Show("Plexus MD3 Importer", "Warning: Image file $image not found, model will not render properly!" )
				}
			}
		}
		md3 << ''
	}
	def convertMd2(File md2) {
		def f, skinField		new SwingBuilder().build {
			f = frame(title: title, windowClosing: {return}, layout: new MigLayout('fill'), pack: true, show: true) {
				label(text:"Please select which image to use for the skin", constraints:('wrap, growy 0, span'))				panel(layout: new MigLayout('wrap 3, fill'), constraints: 'growy, growx 0, span, wrap') {
					for (im in getImages()) {
						def fn = im.file.getName(), full = im.file.getAbsolutePath()
						if (im.image && im.image.width > 64 && im.image.height > 64) {
							def label = "$fn ($im.image.width x $im.image.height)" 
							def b = button(text: label, actionPerformed: { skinField.text = "$fn" }, icon: new ImageIcon(full))
							b.setVerticalTextPosition(SwingConstants.TOP);
					    	b.setHorizontalTextPosition(SwingConstants.CENTER);
						}
					}
				}
//				label( constraints: ("wrap, grow 0, span"))
				label(text: 'Skin to use:', constraints: 'growy 0')
				skinField = textField( constraints: ("wrap, growy 0, growx"))
				button(text: "Convert", actionPerformed: { if (doMD2Convert(skinField.text)) { f.dispose(); return}  }, constraints: ('wrap, growy 0, span'))
				button(text: "Cancel", actionPerformed: {f.dispose(); return }, constraints: 'growy 0, span')
			}
			skinField.setSize(100, 20)
			f.setLocation(500, 500)
			f.size = [1025, (int)f.size.height] as Dimension
		}
		
		//MessageBox.Show(title, "Success!")
	}
	def doMD2Convert(skin) {
		if (!skin) {
			MessageBox.Show(title, "Error: No skin chosen")
			return false
		}
		
		copyFile("tris.md2")
		copyFile("weapon.md2")
		copyFile(skin)
		def f = new File(dstDir, skin)
		if (skin != 'skin.png') {
			def d = new File(dstDir, 'skin.png')
			if (d.isFile()) d.delete()
			f.renameTo(d)
		}
		copyThumb(skin)
		
		writeMd2Config()
		MessageBox.Show(title, "Success!")
		return true;
	}
	def copyThumb(base) {
		copyFile('thumb.png')
		def f = new File(dstDir, 'thumb.png')
		if (!f.isFile()) copyFile(base, 'thumb.png')
	}
	def writeMd2Config() {
		def f = new File(dstDir, 'md2.cfg')
		f.withWriter{ writer ->
	       writer << "// MD2 model converted by Plexus Convertor V1.0\n"
	       writer << "mdlspec -1 // turn off speculars\n"
	       writer << "mdlscale 100 // keep original scale\n"
	       writer << "mdltrans 0 0 25 // raise feet up to surface plane\n"
		   } 
	}
	def copyFile(fn) {
		copyFile(fn, fn)
	}
	def copyFile(from, to) {
		def s = new File(srcDir, from), d = new File(dstDir, to)
		if (!s.isFile()) return
	    def input
	    def output

	    try
	    {
	      input = new BufferedInputStream(new FileInputStream(s))
	      output = new BufferedOutputStream(new FileOutputStream(d))

	      output << input
	    }
	    finally
	    {
	       input?.close()
	       output?.close()
	    }

	}
    Object getImages() {
        imageMaps = [];

        for(i in srcDir.listFiles()) {
            if(!i.isDirectory()) {
                def subpath = i.path;
                if(subpath.startsWith(srcDir.path)) {
                    subpath = subpath.substring(srcDir.path.length());
                }
               def  matcher = subpath =~ pattern;
                if(matcher.find()) {
                    imageMaps.add(["file":i,"matcher":matcher]);
                }
            }
        }
        imageMaps.each({def f = it["file"]; it["image"] = ImageIO.read(it["file"]); });
        return imageMaps;
    }
    
    def getPackages() {
    	def packs = []
        for(i in srcDir.listFiles()) {
            if(!i.isDirectory()) {
                def subpath = i.path;
                if(subpath.startsWith(srcDir.path)) {
                    subpath = subpath.substring(srcDir.path.length());
                }
               def  matcher = subpath =~ ~".pk3";
                if(matcher.find()) {
                    packs.add(i);
                }
            }
        }
    	return packs
    }
}