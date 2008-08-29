import Test;

public class DFMapFileFilter extends javax.swing.filechooser.FileFilter {
    public boolean accept(File f) {
        return f.isDirectory() || f.getName().toLowerCase().endsWith(".txt")
    }
    public String getDescription() {
        return "DF ASCII Maps (*.txt)";
    }
}

public class DFMapBuilder {
	Test m_plexus
	def static cubesize = 32  // the size in sauer to make each square from the DF map
	
	// call this function to build a new DF map from the file passed in
	def buildMap(plexus, filename) {
		m_plexus = plexus
		m_plexus.sauer('newmap', 'if (= 1 $editing) [ edittoggle ]; tc_allowedit 1; thirdperson 0; newmap 13; musicvol 0')
		loadFromFile(filename)

		m_plexus.sauer("spawn", "selcube 32 32 4088 1 1 1 32 5; ent.yaw p0 135; newent playerstart; tc_respawn p0")
		m_plexus.sauer('finished', 'remip; calclight 3; tc_allowedit 0; thirdperson 1')
		m_plexus.dumpCommands()
	}
	// read in and process a whole file
	def loadFromFile(filename) {
		def dfmap = new File(filename);
		def dfmaprow = dfmap.readLines();
		def rows = dfmaprow.size
		for (def i = 0; i < rows; ++i ){
			def dfmapcol = dfmaprow.get(i)
			def dfmapcolarray = dfmapcol.split('')
			def rle = 0, cols = dfmapcolarray.length
			println "cols: $cols, line: $dfmapcol"
			for (def j = 0; j < cols; j += rle) {
				// get chunks of identical letters to process them all the same
				rle = getRLE(dfmapcolarray, j, cols)
				if (dfmapcolarray[j] == 'X') {
					//println "i got an rle of $rle at index: $j"
					def x = j * cubesize;
					def y = i * cubesize;
					m_plexus.sauer('delcube', "selcube $x $y 4088 $rle 1 1 $cubesize 5; delcube; editmat water")
					m_plexus.dumpCommands()
				}
			}
		}
	}
	// this scans across the array to return a count of homogenous letters following the current index
	// this allow processing them as chunks when talking to sauer,  cols is the length of the array
	def getRLE(dfmapcolarray, j, cols) {
		def start = j
		def dfblocktype = dfmapcolarray[j]
		for (++j; j < cols; ++j) {
			// could potentially group letters here if they have the same meaning for performance
			// but for now every letter is considered something different
			if (dfblocktype != dfmapcolarray[j]) break
		}
		return j - start
	}
}