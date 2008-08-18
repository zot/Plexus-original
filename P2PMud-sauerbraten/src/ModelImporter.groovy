import javax.swing.UIManagerimport com.jgoodies.looks.plastic.PlasticLookAndFeelimport com.jgoodies.looks.plastic.Plastic3DLookAndFeelimport com.jgoodies.looks.plastic.theme.DesertBlueimport javax.swing.DefaultListModelimport java.awt.FlowLayoutimport net.miginfocom.swing.MigLayout
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

class ModelImporter {	def static props = [:] as Properties	def static defaultProps = [   		importDir: '',   		exportDir: '',   		mogrifyDir: '',   	] as Properties	
	static void main(args) {		//PlasticLookAndFeel.setPlasticTheme(new DesertBlue());		try {		   UIManager.setLookAndFeel(new Plastic3DLookAndFeel());		} catch (Exception e) {}				readProps()
		showGui()
	}
	def static readProps() {		def dir = new File('fred').getAbsoluteFile().getParent()		def propsFile = new File(dir, 'modelimporter.properties')		if (propsFile.exists()) {			def input = new FileInputStream(propsFile)				props = [:] as Properties			props.load(input)			input.close()		}		// if there are any missing props after a read, fill them in with defaults		for (e in defaultProps) {			if (!props[e.key]) props[e.key] = e.value		}	}	def static saveProps(imp, exp) {		props['importDir'] = imp		props['exportDir'] = exp		def dir = new File('fred').getAbsoluteFile().getParent()		def propsFile = new File(dir, 'modelimporter.properties')		def output = propsFile.newOutputStream()		props.store(output, "Plexus ModelImporter Properties")		output.close()	}	
	static 	def showGui() {
		def f, importField, exportField
		new SwingBuilder().build {
				f = dialog(title: 'Plexus .md2/.md3 Importer', windowClosing: {System.exit(0)}, layout: new MigLayout('fillx'), pack: true, show: true) {
				
				label(text:"Please provide an import and export path", constraints:('span 2, wrap'))
				button(text: "Import...", actionPerformed: { fileImport(importField) })
				importField = textField(text: props.importDir, constraints: 'growx, wrap')
				button(text: "Export...", actionPerformed: { fileExport(exportField) })
				exportField = textField(text: props.exportDir, constraints: 'growx, wrap')
				button(text: "Convert", actionPerformed: { convertModel(importField.text, exportField.text) }, constraints: ('wrap'))
				button(text: "Exit", actionPerformed: {f.dispose(); System.exit(0) })
			}			f.setLocation(500, 500)
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
	static def convertModel(impPath, expPath) {		saveProps(impPath, expPath)
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
	def license	
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
		def md2 = new File(srcDir, "tris.md2")		def md3 = new File(srcDir, "animation.cfg")				// if we can't find either type of model, try unzipping files		if (!md2.isFile() && !md3.isFile()) {			def packs = getPackages()			if (packs) {				println "Going to unpack zips/packages"				for (p in packs) Unzip.Unzipper(p, srcDir, false)			}		}				if (!md2.isFile() && !md3.isFile()) {			MessageBox.Show(title, "Error: Import folder doesn't contain an .md2 or .md3 model")  			return		}				// abort if the user won't accept the license		if (!handleLicense()) return			
		mogrify("pcx")
		mogrify("tga")
		mogrify("tif")
		mogrify("gif")
		
		if (md2.isFile()) return convertMd2(md2)
		if (md3.isFile()) return convertMd3(md3)
	}
	def handleLicense() {		// make the user review and accept the license agreement		def files = getReadme()		def f, mylist, contentsPane		def model =  new DefaultListModel()		new SwingBuilder().build {				f = dialog(title: title, layout: new MigLayout('fillx'), pack: true, modal: true) {								label(text:'Please identify which file below is the license file and that the files you are uploading are freely distributable', constraints:('span 2, wrap'))				label(text:'Users found uploading copyrighted material will be banned!', constraints:('span 2, wrap'))				mylist = list(model: model, valueChanged: { def tmp = files[mylist.selectedIndex]; contentsPane.setPage(tmp.toURL()); },  size: [100, 400])				scrollPane(constraints: ('wrap'), size: [700, 400]) {					contentsPane = textPane(text: '<Please select the license file from the list>', contentType: 'text/plain')				}				button(text: "Cancel", actionPerformed: { license = ''; f.show(false);})				button(text: "I have read and verified the license is acceptible", actionPerformed: {  license = files[mylist.selectedIndex].getName(); f.show(false); })			}			f.setLocation(550, 550)			f.size = [800, 600] as Dimension			for (foo in files) { model.add(0, foo.getName().toString()) }			mylist.selectedIndex = 0;			//f.pack()			f.visible = true		}		return license	}
	def mogrify(ext) {
		def dir = srcDir.getAbsolutePath()
		def cmd = "${ModelImporter.props.mogrifyDir}/mogrify -format png -type TrueColor -depth 16 $dir/*.$ext"
		//println "Mogrify Cmd: $cmd"
		def proc = Runtime.getRuntime().exec(cmd)
		proc.waitFor()
	}
	
	def convertMd3(File animcfg) {
		def lower = new File(srcDir, "lower_default.skin").readLines()
		def upper = new File(srcDir, "upper_default.skin").readLines()
		def head = new File(srcDir, "head_default.skin").readLines()

		def md3 = [], needCopy = [ 'animation.cfg', license ]
		md3 << "// MD3 model converted by Plexus Convertor V1.0"
		copyMD3Section(md3, 'lower', lower, needCopy)
				// clamp the pitch for md3s		md3 << "md3pitch 1 0 -30 30"		
		// extract only the animation lines from animation.cfg
		def lines = animcfg.readLines(), anims = []
		for (line in lines) {
			if (line && Character.isDigit(line[0] as Character)) {
				//println line
				anims << line
			}
		}
				def torsoStart = anims[13] =~ /\d+/, legStart = anims[6] =~  /\d+/ 		//println "ts: ${torsoStart[0]} ls: ${legStart[0]}"		def legOffset = Integer.parseInt(torsoStart[0].toString() ) - Integer.parseInt(legStart[0].toString())		//println ("leg anim offset: $legOffset")		
		// build lower animations
		extractAnimation(md3, "dying", anims, 0, 0)
		extractAnimation(md3, "dead", anims, 1, 0)
		extractAnimation(md3, "lag|edit", anims, 13, legOffset, true) // single frame
		extractAnimation(md3, "forward|left|right", anims, 14, legOffset)
		extractAnimation(md3, "backward", anims, 16, legOffset)
		extractAnimation(md3, "swim", anims, 17, legOffset)
		extractAnimation(md3, "jump", anims, 18, legOffset)
		extractAnimation(md3, "idle", anims, 22, legOffset)
		
		copyMD3Section(md3, 'upper', upper, needCopy)
		
		// build uppper animations
		extractAnimation(md3, "dying", anims, 0, 0)
		extractAnimation(md3, "dead", anims, 1, 0)
		extractAnimation(md3, "idle|lag|edit", anims, 11, 0, true) // single frame
		extractAnimation(md3, "shoot", anims, 7, 0)
		extractAnimation(md3, "punch", anims, 8, 0)
		//extractAnimation(md3, "pain", anims, 22, 0)
		extractAnimation(md3, "taunt", anims, 6, 0)
		
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
	def extractAnimation(md3, name, anims, index, animOffset, single) {
		def first, length, speed
		def line = anims[index]
		def pattern = /(\d+)\s+(\d+)\s+(\d+)\s+(\d+)/
		def m = anims[index] =~ pattern
		first = Integer.parseInt(m[0][1])
		length = single ? 1 : m[0][2]
		speed = m[0][4]
		if (animOffset) first -= animOffset
		if (name.contains('|')) name = '"' + name + '"'
		def out = "md3anim $name $first $length $speed"
		println out
		md3 << out
	}

	def copyMD3Section(md3, section, lines, needCopy) {
		needCopy << "${section}.md3"
		md3 << ''
		md3 << ("md3load ${section}.md3")
		def pattern = /([^,]+),([^,]+)/
		for (line in lines) {
			def m = line =~ pattern			if (m) {				def first = m[0][1]
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
				}			}
		}
		md3 << ''
	}
	def convertMd2(File md2) {
		def f, skinField		new SwingBuilder().build {
			f = dialog(title: title, windowClosing: {return}, layout: new MigLayout('fill'), pack: true, modal: true) {
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
				button(text: "Cancel", actionPerformed: {f.show(false); return }, constraints: 'growy 0, span')
			}
			skinField.setSize(100, 20)
			f.setLocation(550, 550)
			f.size = [1025, (int)f.size.height] as Dimension			f.show(true)
		}
		
		//MessageBox.Show(title, "Success!")
	}
	def doMD2Convert(skin) {
		if (!skin) {
			MessageBox.Show(title, "Error: No skin chosen")
			return false
		}
				copyFile(license)
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
	       writer << "// MD2 model converted by Plexus Convertor V1.0\n"	       // clamp the pitch for md2s	       writer << "md2pitch 1 0 -30 30"	       writer << "mdlspec -1 // turn off speculars\n"
	       writer << "mdlscale 100 // keep original scale\n"
	       writer << "mdltrans 0 0 24 // raise feet up to surface plane\n"
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
        def getPackages() {    	return getFiles(~".pk3|.zip")    }    def getReadme() {    	return getFiles(~"readme|help|.txt|.html")    }    def getFiles(pattern) {
    	def packs = []
        for(i in srcDir.listFiles()) {
            if(!i.isDirectory()) {
                def subpath = i.path;
                if(subpath.startsWith(srcDir.path)) {
                    subpath = subpath.substring(srcDir.path.length());
                }
               def  matcher = subpath =~ pattern;
                if(matcher.find()) {
                    packs.add(i);
                }
            }
        }
    	return packs
    }
}