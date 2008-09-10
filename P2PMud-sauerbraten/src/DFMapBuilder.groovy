import Plexus;
import java.awt.Dimension;


public class DFMapBuilder {
	Plexus m_plexus
	def static cubesize = 32	// the size in sauer to make each square from the DF map
	def static floorThick = 8	// how thick to make floors
	def map = ['-1':'e',' 0': '?','1':'r','2':'w','3':'?','4':'?','5':'?',' 6':'?',' 7':'?',' 8':'?',' 9':'?', '10':'?', '11':'?', '12':'?', '13':'?', '14':'?', '15':'?', '16':'?', '17':'?', '18':'?', '19':'?', '20':'?', '21':'?', '22':'f', '23':'?', '24':'f', '25':'s', '26':'s', '27':'s', '28':'?', '29':'?', '30':'?', '31':'?','32':'e', '33':'?', '34':'f', '35':'e', '36':'s', '37':'s', '38':'s', '39':'?', '40':'?', '41':'s', '42':'e', '43':'f', '44':'?', '45':'f', '46':'f', '47':'f', '48':'?', '49':'s', '50':'s', '51':'s', '52':'s', '53':'s', '54':'s', '55':'s', '56':'s', '57':'s', '58':'s', '59':'s', '60':'s', '61':'s', '62':'s', '63':'s', '64':'?', '65':'o', '66':'?', '67':'?', '68':'?', '69':'?', '70':'i', '71':'?', '72':'?', '73':'?', '74':'?', '75':'?', '76':'?', '77':'?', '78':'?', '79':'p', '80':'p', '81':'p', '82':'p', '83':'p', '84':'?', '85':'?', '86':'?', '87':'?', '88':'?', '89':'w', '90':'w', /*'91':'?', '92':'?', '93':'?', '94':'?', '95':'?', '96':'?', '97':'?', '98':'?', '99':'?', '100':'?', '100':'?', '101':'?', '102':'?', '103':'?', '104':'?', '105':'?', '106':'?', '107':'?', '108':'?', '109':'?', '110':'?', '111':'?', '112':'?', '113':'?', '114':'?', '115':'?', '116':'?', '117':'?', '118':'?', '119':'?', '120':'?', '121':'?', '122':'?', '123':'?', '124':'?', '125':'?', '126':'?', '127':'?', '128':'?', '129':'?', '130':'?', '131':'?', '132':'?', '133':'?', '134':'?', '135':'?', '136':'?', '137':'?', '138':'?', '139':'?', '140':'?', '141':'?', '142':'?', '143':'?', '144':'?', '145':'?', '146':'?', '147':'?', '148':'?', '149':'?', '150':'?', '151':'?', '152':'?', '153':'?', '154':'?', '155':'?', '156':'?', '157':'?', '158':'?', '159':'?', '160':'?', '161':'?', '162':'?', '163':'?', '164':'?', '165':'?', '166':'?', '167':'?', '168':'?', '169':'?', '170':'?', '171':'?', '172':'?', '173':'?', '174':'?',*/ '175':'?', '176':'?', '177':'?', '178':'?', '179':'?', '180':'?', '181':'?', '182':'?', '183':'?', '184':'?', '185':'?', '186':'?', '187':'?', '188':'?', '189':'?', '190':'?', '191':'?', '192':'?', '193':'?', '194':'?', '195':'?', '196':'?', '197':'?', '198':'?', '199':'?', '200':'?', '201':'?', '202':'?', '203':'?', '204':'?', '205':'?', '206':'?', '207':'?', '208':'?', '209':'?', '210':'?', '211':'?', '212':'?', '213':'?', '214':'?', '215':'?', '216':'l', '217':'l', '218':'l', '219':'l', '220':'l', '221':'l', '222':'?', '223':'?', '224':'?', '225':'?', '226':'?', '227':'?', '228':'?', '229':'?', '230':'?', '231':'f', '232':'?', '233':'r', '234':'r', '235':'r', '236':'r', '237':'r', '238':'r', '239':'r', '240':'r', '241':'r', '242':'f', '243':'f', '244':'f', '245':'r', '246':'?', '247':'?', '248':'?', '249':'?', '250':'?', '251':'?', '252':'?', '253':'?', '254':'?', '255':'?', '256':'?', '257':'?', '258':'c', '259':'c', '260':'c', '261':'f', '262':'?', '263':'?', '264':'v', '265':'l', '266':'?', '267':'?', '268':'l', '269':'l', '270':'l', '271':'l', '272':'l', '273':'l', '274':'l', '275':'l', '276':'l', '277':'l', '278':'l', '279':'l', '280':'l', '281':'l', '282':'l', '283':'l', '284':'l', '285':'l', '286':'l', '287':'l', '288':'l', '289':'l', '290':'l', '291':'l', '292':'l', '293':'l', '294':'l', '295':'l', '296':'l', '297':'l', '298':'l', '299':'l', '300':'l', '301':'l', '302':'l', '303':'l', '304':'l', '305':'l', '306':'l', '307':'l', '308':'l', '309':'l', '310':'l', '311':'l', '312':'l', '313':'l', '314':'l', '315':'l', '316':'l', '317':'l', '318':'l', '319':'l', '320':'l', '321':'l', '322':'l', '323':'l', '324':'l', '325':'o', '326':'o', '327':'l', '328':'l', '329':'l', '330':'l', '331':'l', '332':'l', '333':'l', '334':'l', '335':'l', '336':'f', '337':'f', '338':'f', '339':'f', '340':'f', '341':'f', '342':'f', '343':'f', '344':'f', '345':'f', '346':'f', '347':'f', '348':'f', '349':'f', '350':'f', '351':'f', '352':'f', '353':'f', '354':'f', '355':'f', '356':'f', '357':'f', '358':'f', '359':'f', '360':'o', '361':'l', '362':'l', '363':'l', '364':'l', '365':'w', '366':'w', '367':'w', '368':'w', '369':'w', '370':'w', '371':'w', '372':'w', '373':'w', '374':'w', '375':'w', '376':'w', '377':'w', '378':'w', '379':'w', '380':'w', '381':'e', '382':'f', '383':'f', '384':'f', '385':'f', '386':'f', '387':'f', '388':'f', '389':'f', '390':'f', '391':'f', '392':'f', '393':'f', '394':'f', '395':'f', '396':'f', '397':'f', '398':'f', '399':'f', '400':'f', '401':'f', '402':'f', '403':'f', '404':'f', '405':'f', '406':'f', '407':'f', '408':'f', '409':'f', '410':'f', '411':'f', '412':'f', '413':'f', '414':'f', '415':'f', '416':'f', '417':'l', '418':'l', '419':'l', '420':'l', '421':'l', '422':'l', '423':'l', '424':'l', '425':'l', '426':'l', '427':'l', '428':'l', '429':'l', '430':'l', '431':'l', '432':'l', '433':'l', '434':'l', '435':'l', '436':'o', '437':'l', '438':'l', '439':'l', '440':'l', '441':'f', '442':'f', '443':'f', '444':'f', '445':'f', '446':'f', '447':'f', '448':'f', '449':'f', '450':'l', '451':'l', '452':'l', '453':'l', '454':'l', '455':'l', '456':'l', '457':'l', '458':'l', '459':'l', '460':'l', '461':'l', '462':'l', '463':'l', '464':'l', '465':'l', '466':'l', '467':'l', '468':'l', '469':'l', '470':'l', '471':'l', '472':'l', '473':'?', '474':'?', '475':'?', '476':'?', '477':'?', '478':'?', '479':'?', '480':'?', '481':'?', '482':'?', '483':'?', '484':'?', '485':'?', '486':'?', '487':'?', '488':'?', '489':'?', '490':'f', '491':'f', '492':'f', '493':'f', '494':'o', '495':'p', '496':'l', '497':'l', '498':'l', '499':'l', '500':'l', '501':'l', '502':'l', '503':'l', '504':'l', '505':'l', '506':'l', '507':'l', '508':'l', '509':'l', '510':'l', '511':'l', '512':'l', '513':'l', '514':'l', '515':'s', '516':'?', '517':'?', '518':'r', '519':'?', '520':'?']
	// call this function to build a new DF map from the file passed in
	def buildMap(plexus, filename) {
		m_plexus = plexus
		m_plexus.sauer('newmap', 'if (= 1 $editing) [ edittoggle ]; tc_allowedit 1; thirdperson 0; newmap 13; musicvol 0; undomegs 0')
		loadFromFile(filename)

		m_plexus.sauer("spawn", "selcube 32 32 4088 1 1 1 32 5; ent.yaw p0 135; newent playerstart; tc_respawn p0")
		m_plexus.sauer('finished', 'remip;  tc_allowedit 0; thirdperson 1')  // calclight 3;
		m_plexus.dumpCommands()
	}
	// read in and process a whole file
	def loadFromFile(filename) {
		println "Going to load a DF from from $filename"
		def dfmap = new File(filename);
		def dfmaprow = dfmap.readLines();
		def info = dfmaprow.get(0).split(",")
		def layers = info[1].toInteger()
		def rows = ((dfmaprow.size - layers) / layers)
		def totalrows = dfmaprow.size();
		println "Rows in file: $totalrows"
		def z = 0
		def remipCount = 0
		def ir = 0
		def h = 0; //Math.round(i / rows) //sets height
		for (def i = 1; i < totalrows; ++i ){
			def dfmapcol = dfmaprow.get(i)
			ir ++;
			//def ir = Math.round(i - (h * rows)) //resets row
			def dfmapcolarray = dfmapcol.split()
			def rle = 0, cols = dfmapcolarray.length
			if (cols < 3){
				println "i: $i, New layer found: " + dfmapcolarray.toString()
				ir = 0;
				h++;
				z = 3072 + h * cubesize;
			} else {
			//println "cols: $cols, line: $dfmapcol"
			for (def j = 0; j < cols; j += rle) {
				// get chunks of identical letters to process them all the same
				rle = getRLE(dfmapcolarray, j, cols)
				def first = map[dfmapcolarray[j]]
				if (first != '$') {
					//println "i got an rle of $rle at index: $j  first is $first"
					def x = j * cubesize;
					def y = ir * cubesize;
					if (first == 'f') {
						def z1 = z + floorThick, len = Math.round(rle * cubesize / floorThick), floor = Math.round((cubesize  - floorThick) / floorThick), foo = Math.round(cubesize / floorThick)
						//println  "selcube $x $y $z1 $len $foo $floor $floorThick 5; delcube"
						m_plexus.sauer('delcube', "selcube $x $y $z1 $len $foo $floor $floorThick 5; delcube")
						remipCount += rle
					}else if (first == 'w') {
						m_plexus.sauer('delcube', "selcube $x $y $z $rle 1 1 $cubesize 5; delcube; editmat water")
					} else if(first == 'e'){
						m_plexus.sauer('delcube', "selcube $x $y $z $rle 1 1 $cubesize 5; delcube")
				 	} else if(first == 's') {
				 		m_plexus.sauer('delcube', "selcube $x $y $z $rle 1 1 $cubesize 5; delcube")
					}
				}
				m_plexus.dumpCommands()
			}
			
			if (remipCount > 25000) {
				m_plexus.sauer('remip', 'remip')
				m_plexus.dumpCommands()
				remipCount = 0
			}
			}
		}
		//while (z < 4064) {
	 	//	m_plexus.sauer('delcube', "selcube 0 0 $z 7500 7500 1 $cubesize 5; delcube")
		//	m_plexus.dumpCommands()
		//	
		//	z += 32
		//}
	}
	// 7552, 7680, 4064

	// this scans across the array to return a count of homogenous letters following the current index
	// this allow processing them as chunks when talking to sauer,  cols is the length of the array
	def getRLE(dfmapcolarray, j, cols) {
		def start = j
		def dfblocktype = map[dfmapcolarray[j]]
		for (++j; j < cols; ++j) {
			// could potentially group letters here if they have the same meaning for performance
			// but for now every letter is considered something different
			if (dfblocktype != map[dfmapcolarray[j]]) break
		}
		return j - start
	}
}