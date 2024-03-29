import Plexus;
import java.awt.Dimension;


public class DFMapBuilder {
	Plexus m_plexus
	def static cubesize = 32	// the size in sauer to make each square from the DF map
	def static floorThick = 8	// how thick to make floors
	def map = ['-1':'e',' 0': '?','1':'r','2':'w','3':'?','4':'?','5':'?',' 6':'?',' 7':'?',' 8':'?',' 9':'?', '10':'?', '11':'?', '12':'?', '13':'?', '14':'?', '15':'?', '16':'?', '17':'?', '18':'?', '19':'?', '20':'?', '21':'?', '22':'f', '23':'?', '24':'f', '25':'s', '26':'s', '27':'s', '28':'?', '29':'?', '30':'?', '31':'?','32':'e', '33':'?', '34':'f', '35':'e', '36':'s', '37':'s', '38':'s', '39':'?', '40':'?', '41':'s', '42':'e', '43':'f', '44':'?', '45':'f', '46':'f', '47':'f', '48':'?', '49':'s', '50':'s', '51':'s', '52':'s', '53':'s', '54':'s', '55':'s', '56':'s', '57':'s', '58':'s', '59':'s', '60':'s', '61':'s', '62':'s', '63':'s', '64':'?', '65':'o', '66':'?', '67':'?', '68':'?', '69':'?', '70':'i', '71':'?', '72':'?', '73':'?', '74':'?', '75':'?', '76':'?', '77':'?', '78':'?', '79':'p', '80':'p', '81':'p', '82':'p', '83':'p', '84':'?', '85':'?', '86':'?', '87':'?', '88':'?', '89':'w', '90':'w', /*'91':'?', '92':'?', '93':'?', '94':'?', '95':'?', '96':'?', '97':'?', '98':'?', '99':'?', '100':'?', '100':'?', '101':'?', '102':'?', '103':'?', '104':'?', '105':'?', '106':'?', '107':'?', '108':'?', '109':'?', '110':'?', '111':'?', '112':'?', '113':'?', '114':'?', '115':'?', '116':'?', '117':'?', '118':'?', '119':'?', '120':'?', '121':'?', '122':'?', '123':'?', '124':'?', '125':'?', '126':'?', '127':'?', '128':'?', '129':'?', '130':'?', '131':'?', '132':'?', '133':'?', '134':'?', '135':'?', '136':'?', '137':'?', '138':'?', '139':'?', '140':'?', '141':'?', '142':'?', '143':'?', '144':'?', '145':'?', '146':'?', '147':'?', '148':'?', '149':'?', '150':'?', '151':'?', '152':'?', '153':'?', '154':'?', '155':'?', '156':'?', '157':'?', '158':'?', '159':'?', '160':'?', '161':'?', '162':'?', '163':'?', '164':'?', '165':'?', '166':'?', '167':'?', '168':'?', '169':'?', '170':'?', '171':'?', '172':'?', '173':'?', '174':'?',*/ '175':'?', '176':'?', '177':'?', '178':'?', '179':'?', '180':'?', '181':'?', '182':'?', '183':'?', '184':'?', '185':'?', '186':'?', '187':'?', '188':'?', '189':'?', '190':'?', '191':'?', '192':'?', '193':'?', '194':'?', '195':'?', '196':'?', '197':'?', '198':'?', '199':'?', '200':'?', '201':'?', '202':'?', '203':'?', '204':'?', '205':'?', '206':'?', '207':'?', '208':'?', '209':'?', '210':'?', '211':'?', '212':'?', '213':'?', '214':'?', '215':'?', '216':'l', '217':'l', '218':'l', '219':'l', '220':'l', '221':'l', '222':'?', '223':'?', '224':'?', '225':'?', '226':'?', '227':'?', '228':'?', '229':'?', '230':'?', '231':'f', '232':'?', '233':'r', '234':'r', '235':'r', '236':'r', '237':'r', '238':'r', '239':'r', '240':'r', '241':'r', '242':'f', '243':'f', '244':'f', '245':'r', '246':'?', '247':'?', '248':'?', '249':'?', '250':'?', '251':'?', '252':'?', '253':'?', '254':'?', '255':'?', '256':'?', '257':'?', '258':'c', '259':'c', '260':'c', '261':'f', '262':'?', '263':'?', '264':'v', '265':'l', '266':'?', '267':'?', '268':'l', '269':'l', '270':'l', '271':'l', '272':'l', '273':'l', '274':'l', '275':'l', '276':'l', '277':'l', '278':'l', '279':'l', '280':'l', '281':'l', '282':'l', '283':'l', '284':'l', '285':'l', '286':'l', '287':'l', '288':'l', '289':'l', '290':'l', '291':'l', '292':'l', '293':'l', '294':'l', '295':'l', '296':'l', '297':'l', '298':'l', '299':'l', '300':'l', '301':'l', '302':'l', '303':'l', '304':'l', '305':'l', '306':'l', '307':'l', '308':'l', '309':'l', '310':'l', '311':'l', '312':'l', '313':'l', '314':'l', '315':'l', '316':'l', '317':'l', '318':'l', '319':'l', '320':'l', '321':'l', '322':'l', '323':'l', '324':'l', '325':'o', '326':'o', '327':'l', '328':'l', '329':'l', '330':'l', '331':'l', '332':'l', '333':'l', '334':'l', '335':'l', '336':'f', '337':'f', '338':'f', '339':'f', '340':'f', '341':'f', '342':'f', '343':'f', '344':'f', '345':'f', '346':'f', '347':'f', '348':'f', '349':'f', '350':'f', '351':'f', '352':'f', '353':'f', '354':'f', '355':'f', '356':'f', '357':'f', '358':'f', '359':'f', '360':'o', '361':'l', '362':'l', '363':'l', '364':'l', '365':'w', '366':'w', '367':'w', '368':'w', '369':'w', '370':'w', '371':'w', '372':'w', '373':'w', '374':'w', '375':'w', '376':'w', '377':'w', '378':'w', '379':'w', '380':'w', '381':'e', '382':'f', '383':'f', '384':'f', '385':'f', '386':'f', '387':'f', '388':'f', '389':'f', '390':'f', '391':'f', '392':'f', '393':'f', '394':'f', '395':'f', '396':'f', '397':'f', '398':'f', '399':'f', '400':'f', '401':'f', '402':'f', '403':'f', '404':'f', '405':'f', '406':'f', '407':'f', '408':'f', '409':'f', '410':'f', '411':'f', '412':'f', '413':'f', '414':'f', '415':'f', '416':'f', '417':'l', '418':'l', '419':'l', '420':'l', '421':'l', '422':'l', '423':'l', '424':'l', '425':'l', '426':'l', '427':'l', '428':'l', '429':'l', '430':'l', '431':'l', '432':'l', '433':'l', '434':'l', '435':'l', '436':'o', '437':'l', '438':'l', '439':'l', '440':'l', '441':'f', '442':'f', '443':'f', '444':'f', '445':'f', '446':'f', '447':'f', '448':'f', '449':'f', '450':'l', '451':'l', '452':'l', '453':'l', '454':'l', '455':'l', '456':'l', '457':'l', '458':'l', '459':'l', '460':'l', '461':'l', '462':'l', '463':'l', '464':'l', '465':'l', '466':'l', '467':'l', '468':'l', '469':'l', '470':'l', '471':'l', '472':'l', '473':'?', '474':'?', '475':'?', '476':'?', '477':'?', '478':'?', '479':'?', '480':'?', '481':'?', '482':'?', '483':'?', '484':'?', '485':'?', '486':'?', '487':'?', '488':'?', '489':'?', '490':'f', '491':'f', '492':'f', '493':'f', '494':'o', '495':'p', '496':'l', '497':'l', '498':'l', '499':'l', '500':'l', '501':'l', '502':'l', '503':'l', '504':'l', '505':'l', '506':'l', '507':'l', '508':'l', '509':'l', '510':'l', '511':'l', '512':'l', '513':'l', '514':'l', '515':'s', '516':'?', '517':'?', '518':'r', '519':'?', '520':'?']
	def texmap = ['-1':'e',' 0': '?','1':'r','2':'dirt','3':'?','4':'?','5':'?',' 6':'?',' 7':'?',' 8':'?',' 9':'?', '10':'?', '11':'?', '12':'?', '13':'?', '14':'?', '15':'?', '16':'?', '17':'?', '18':'?', '19':'?', '20':'?', '21':'?', '22':'f', '23':'?', '24':'tree', '25':'s', '26':'s', '27':'s', '28':'?', '29':'?', '30':'?', '31':'?','32':'e', '33':'?', '34':'grass', '35':'e', '36':'stone', '37':'stone', '38':'stone', '39':'dirt', '40':'dirt', '41':'dirt', '42':'e', '43':'carvedstone', '44':'carvedstone', '45':'carvedstone', '46':'carvedstone', '47':'frozen', '48':'?', '49':'grass', '50':'grass', '51':'grass', '52':'grass', '53':'grass', '54':'stone', '55':'stone', '56':'stone', '57':'stone', '58':'stone', '59':'stone', '60':'stone', '61':'stone', '62':'stone', '63':'stone', '64':'?', '65':'stone', '66':'?', '67':'?', '68':'?', '69':'?', '70':'i', '71':'?', '72':'?', '73':'?', '74':'?', '75':'?', '76':'?', '77':'?', '78':'?', '79':'stone', '80':'stone', '81':'stone', '82':'stone', '83':'stone', '84':'?', '85':'?', '86':'?', '87':'?', '88':'?', '89':'frozen', '90':'frozen', /*'91':'?', '92':'?', '93':'?', '94':'?', '95':'?', '96':'?', '97':'?', '98':'?', '99':'?', '100':'?', '100':'?', '101':'?', '102':'?', '103':'?', '104':'?', '105':'?', '106':'?', '107':'?', '108':'?', '109':'?', '110':'?', '111':'?', '112':'?', '113':'?', '114':'?', '115':'?', '116':'?', '117':'?', '118':'?', '119':'?', '120':'?', '121':'?', '122':'?', '123':'?', '124':'?', '125':'?', '126':'?', '127':'?', '128':'?', '129':'?', '130':'?', '131':'?', '132':'?', '133':'?', '134':'?', '135':'?', '136':'?', '137':'?', '138':'?', '139':'?', '140':'?', '141':'?', '142':'?', '143':'?', '144':'?', '145':'?', '146':'?', '147':'?', '148':'?', '149':'?', '150':'?', '151':'?', '152':'?', '153':'?', '154':'?', '155':'?', '156':'?', '157':'?', '158':'?', '159':'?', '160':'?', '161':'?', '162':'?', '163':'?', '164':'?', '165':'?', '166':'?', '167':'?', '168':'?', '169':'?', '170':'?', '171':'?', '172':'?', '173':'?', '174':'?',*/ '175':'?', '176':'stonebrick', '177':'stonebrick', '178':'stonebrick', '179':'?', '180':'?', '181':'?', '182':'?', '183':'?', '184':'?', '185':'?', '186':'?', '187':'?', '188':'?', '189':'?', '190':'?', '191':'?', '192':'?', '193':'?', '194':'?', '195':'?', '196':'?', '197':'?', '198':'?', '199':'?', '200':'?', '201':'?', '202':'?', '203':'?', '204':'?', '205':'?', '206':'?', '207':'?', '208':'?', '209':'?', '210':'?', '211':'?', '212':'?', '213':'?', '214':'?', '215':'?', '216':'l', '217':'l', '218':'l', '219':'stonebrick', '220':'l', '221':'l', '222':'?', '223':'?', '224':'?', '225':'?', '226':'?', '227':'?', '228':'?', '229':'?', '230':'?', '231':'grass', '232':'?', '233':'grass', '234':'grass', '235':'grass', '236':'grass', '237':'stone', '238':'stone', '239':'stone', '240':'stone', '241':'dirt', '242':'dirt', '243':'dirt', '244':'dirt', '245':'frozen', '246':'?', '247':'?', '248':'?', '249':'?', '250':'?', '251':'?', '252':'?', '253':'?', '254':'?', '255':'?', '256':'?', '257':'?', '258':'frozen', '259':'frozen', '260':'frozen', '261':'dirt', '262':'frozen', '263':'?', '264':'v', '265':'dirt', '266':'?', '267':'?', '268':'stone', '269':'stone', '270':'stone', '271':'stone', '272':'stone', '273':'stone', '274':'stone', '275':'stone', '276':'stone', '277':'stone', '278':'stone', '279':'stone', '280':'stone', '281':'stone', '282':'stone', '283':'stone', '284':'stone', '285':'stone', '286':'stone', '287':'stone', '288':'stone', '289':'stone', '290':'stone', '291':'stone', '292':'stone', '293':'stone', '294':'stone', '295':'stone', '296':'stone', '297':'stone', '298':'stone', '299':'stone', '300':'stone', '301':'stone', '302':'stone', '303':'stone', '304':'stone', '305':'stone', '306':'stone', '307':'stone', '308':'stone', '309':'stone', '310':'stone', '311':'stone', '312':'stone', '313':'stone', '314':'stone', '315':'stone', '316':'stone', '317':'stone', '318':'stone', '319':'stone', '320':'stone', '321':'stone', '322':'stone', '323':'stone', '324':'stone', '325':'o', '326':'o', '327':'stone', '328':'stone', '329':'stone', '330':'stone', '331':'stone', '332':'stone', '333':'stone', '334':'stone', '335':'stone', '336':'stone', '337':'stone', '338':'stone', '339':'stone', '340':'stone', '341':'stone', '342':'stone', '343':'stone', '344':'stone', '345':'stone', '346':'stone', '347':'stone', '348':'grass', '349':'grass', '350':'grass', '351':'grass', '352':'dirt', '353':'dirt', '354':'dirt', '355':'dirt', '356':'dirt', '357':'dirt', '358':'dirt', '359':'dirt', '360':'frozen', '361':'frozen', '362':'frozen', '363':'frozen', '364':'frozen', '365':'river', '366':'river', '367':'river', '368':'river', '369':'river', '370':'river', '371':'river', '372':'river', '373':'river', '374':'river', '375':'river', '376':'river', '377':'river', '378':'river', '379':'river', '380':'river', '381':'e', '382':'f', '383':'f', '384':'f', '385':'f', '386':'f', '387':'grass', '388':'grass', '389':'grass', '390':'grass', '391':'grass', '392':'grass', '393':'grass', '394':'grass', '395':'grass', '396':'grass', '397':'grass', '398':'grass', '399':'grass', '400':'grass', '401':'grass', '402':'stone', '403':'stone', '404':'stone', '405':'stone', '406':'stone', '407':'stone', '408':'stone', '409':'stone', '410':'stone', '411':'stone', '412':'stone', '413':'stone', '414':'stone', '415':'stone', '416':'stone', '417':'l', '418':'l', '419':'l', '420':'l', '421':'l', '422':'l', '423':'l', '424':'l', '425':'l', '426':'l', '427':'l', '428':'l', '429':'l', '430':'l', '431':'l', '432':'l', '433':'l', '434':'l', '435':'l', '436':'o', '437':'l', '438':'l', '439':'l', '440':'l', '441':'stone', '442':'stone', '443':'stone', '444':'stone', '445':'stone', '446':'stone', '447':'stone', '448':'stone', '449':'stone', '450':'l', '451':'l', '452':'l', '453':'l', '454':'l', '455':'l', '456':'l', '457':'l', '458':'l', '459':'l', '460':'l', '461':'l', '462':'l', '463':'l', '464':'l', '465':'l', '466':'l', '467':'l', '468':'l', '469':'l', '470':'l', '471':'l', '472':'l', '473':'?', '474':'?', '475':'?', '476':'?', '477':'?', '478':'?', '479':'?', '480':'?', '481':'?', '482':'?', '483':'?', '484':'?', '485':'?', '486':'?', '487':'?', '488':'?', '489':'?', '490':'f', '491':'f', '492':'f', '493':'f', '494':'o', '495':'p', '496':'stonebrick', '497':'stonebrick', '498':'stonebrick', '499':'stonebrick', '500':'stonebrick', '501':'stonebrick', '502':'stonebrick', '503':'stonebrick', '504':'stonebrick', '505':'stonebrick', '506':'stonebrick', '507':'stonebrick', '508':'stonebrick', '509':'stonebrick', '510':'stonebrick', '511':'stonebrick', '512':'stonebrick', '513':'stonebrick', '514':'stonebrick', '515':'s', '516':'?', '517':'?', '518':'r', '519':'?', '520':'?']
	def texnum = ['grass':8 ,'stone':32 ,'stonebrick':3,'dirt':20,'river':2,'frozen':7,'?':8,'e':1,'r':2,'carvedstone':5,'f':3,'l':3,'s':3,'tree':8]
	def mapmodel = ['24':'; newent mapmodel 1 1']//; dropent 2 need to get it to the ground
	def sauer(label, cmd) {
		m_plexus.exec {
			m_plexus.sauer(label, cmd)
		}
	}
	def dumpCommands() {
		m_plexus.exec {
			m_plexus.dumpCommands()
		}
	}
	// call this function to build a new DF map from the file passed in
	def buildMap(plexus, filename) {
		m_plexus = plexus
		sauer('newmap', 'if (= 1 $editing) [ edittoggle ]; tc_allowedit 1; thirdperson 0; newmap 13; musicvol 0; undomegs 0')
		//sauer('tex', 'texturereset; setshader stdworld; exec packages/egyptsoc/package.cfg ')
		loadFromFile(filename)
		sauer("spawn", "selcube 32 32 4088 1 1 1 32 5; ent.yaw p0 135; newent playerstart; tc_respawn p0")
		sauer('finished', 'remip;  tc_allowedit 0; thirdperson 1')  // calclight 3;
		dumpCommands()
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
		def oldlinestore = [ 0:'0'] //will store previous line's data
		def currentlinestore = [ 0:'0'] //will store current line's data
		for (def i = 1; i < totalrows; ++i ){
			def dfmapcol = dfmaprow.get(i)
			ir ++;
			//def ir = Math.round(i - (h * rows)) //resets row
			def dfmapcolarray = dfmapcol.split()
			def rle = 0, cols = dfmapcolarray.length
			oldlinestore == currentlinestore //turns current line into previous line?
			if (cols < 10){
				println "i: $i, New layer found: " + dfmapcolarray.toString()
				ir = 0;
				h++;
				z = 4096 + h * cubesize;//3072				
			} else {
				//println "cols: $cols, line: $dfmapcol"
				for (def j = 0; j < cols; j += rle) {
					// get chunks of identical letters to process them all the same
					rle = getRLE(dfmapcolarray, j, cols)
					def first = map[dfmapcolarray[j]]
					currentlinestore[j] == first; //suppossed to store the current value into a map, but doesnt :(
					if (first != '$') {
						//println "i got an rle of $rle at index: $j  first is $first"
						def x = j * cubesize;
						def y = ir * cubesize;
						def zx = z - cubesize;
						def zw = zx - cubesize;
						def tex = texnum[texmap[dfmapcolarray[j]]]		
						def texture = "tc_settex $tex 1"
						def mapmodel = mapmodel[dfmapcolarray[j]]
						if (first == 'f') {
							def z1 = z + floorThick, len = Math.round(rle * cubesize / floorThick), floor = Math.round((cubesize  - floorThick) / floorThick), foo = Math.round(cubesize / floorThick)
							//println  "selcube $x $y $z1 $len $foo $floor $floorThick 5; delcube"
							sauer('walltex', "selcube $x $y $zx $rle 1 1 $cubesize 5; $texture; editface -1 1")
							sauer('delcube', "selcube $x $y $z1 $len $foo $floor $floorThick 5; delcube")//$texture
							sauer('wall1', "selcube $x $y $zx $len 1 2 $cubesize 5 $mapmodel")
							remipCount += rle
						} else if (first == 'w') {
							sauer('delcube', "selcube $x $y $z $rle 1 1 $cubesize 5; editmat water")
						}/* else if(first == 'e'){
							sauer('delcube', "selcube $x $y $z $rle 1 1 $cubesize 5; $texture")
					 	}*/  else if(first == 's') {
					 		sauer('delcube', "selcube $x $y $z $rle 1 1 $cubesize 5; $texture")
						}  else if(first == 'l') /*OR (first == 'l') OR (first == 'r')*/  {
							sauer('walltex', "selcube $x $y $zw $rle 2 2 $cubesize 5; $texture; editface -1 1")
						} else if (first == '?') {
							sauer('walltex', "selcube $x $y $zw $rle 2 2 $cubesize 5; $texture; editface -1 1")
						} else if (first == 'r') {
						sauer('walltex', "selcube $x $y $zw $rle 2 2 $cubesize 5; $texture; editface -1 1")}
					}
					dumpCommands();
					println currentlinestore;
				}
				
				if (remipCount > 25000) {
					sauer('remip', 'remip')
					dumpCommands()
					remipCount = 0
				}
			}
		} 

		//while (z //< 4064) {
	 	//	sauer('delcube', "selcube 0 0 $z 7500 7500 1 $cubesize 5; delcube")
		//	dumpCommands()
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
		def dftex = texnum[texmap[dfmapcolarray[j]]]
		for (++j; j < cols; ++j) {
			// could potentially group letters here if they have the same meaning for performance
			// but for now every letter is considered something different
			if (dfblocktype != map[dfmapcolarray[j]]) break
			if (dftex != texnum[texmap[dfmapcolarray[j]]]) break
		}
		return j - start
	}
}