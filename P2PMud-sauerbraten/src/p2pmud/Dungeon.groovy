package p2pmud

import groovy.swing.SwingBuilderimport groovy.swing.SwingBuilderimport net.miginfocom.swing.MigLayoutimport java.awt.Dimensionclass Dungeon {

	/*
	** Maze Generation Javascript provided by:  http://home.att.net/~srschmitt/script_maze_generator.html
	**
	*/
	def ROWS, COLS;                                 // dimensions of maze
	def Maze;                                       // the maze of cells
	def Stack;                                      // cell stack to hold a list of cell locations
	def N = 1;                          // direction constants
	def E = 2;
	def S = 4;
	def W = 8;
	def roomsize = 3, hallsize = 3, cellsz = roomsize + (hallsize * 2)
	def blockRows, blockCols
	
	def public Dungeon(r, c) {
		ROWS = r;
		COLS = c;
		
		init_cells()
	}
	def public Dungeon(int r, int c, int rsize, int hsize) {
		ROWS = r;
		COLS = c;

		roomsize = rsize
		hallsize = hsize
		cellsz = roomsize + (hallsize * 2)
		
		init_cells()
	}
	def int randint( beg, end )
	{
	    return Math.floor(beg + ((end - beg + 1)*Math.random()));
	}

//	 initialize variable sized multidimensional arrays
	def init_cells()
	{
	    def i, j;

	    // create a maze of cells
	    Maze =  new Object[ROWS];
	    for (i = 0; i < ROWS; i++)
	    	Maze[i] = new Object[COLS];

	    // set all walls of each cell in maze by setting bits :  N E S W
	    for (i = 0; i < ROWS; i++)
	        for (j = 0; j < COLS; j++)
	            Maze[i][j] = (N + E + S + W);

	    // create stack for storing previously visited locations
	    Stack = new Object[ROWS*COLS];
	    for (i = 0; i < ROWS*COLS; i++)
	        Stack[i] = new Object[2];

	    // initialize stack
	    for (i = 0; i < ROWS*COLS; i++)
	        for (j = 0; j < 2; j++)
	            Stack[i][j] = 0;
	}

//	 use depth first search to create a maze
	def generate_maze()
	{
	    def i, j, r, c;

	    // choose a cell at random and make it the current cell
	    r = randint( 0, ROWS - 1 );
	    c = randint( 0, COLS - 1 );
	    def curr = [ r, c ];                            // current search location
	    def visited  = 1;
	    def total = ROWS*COLS;
	    def tos   = 0;                              // index for top of cell stack 
	    
	    // arrays of single step movements between cells
	    //          north    east     south    west
	    def move = [[-1, 0], [ 0, 1], [ 1, 0], [ 0,-1]];
	    def next = [[ 0, 0], [ 0, 0], [ 0, 0], [ 0, 0]];
	   
	    while (visited < total)
	    {
	        //  find all neighbors of current cell with all walls intact
	        j = 0;
	        for (i = 0; i < 4; i++)
	        {
	            r = curr[0] + move[i][0];
	            c = curr[1] + move[i][1];

	            //  check for valid next cell
	            if ((0 <= r) && (r < ROWS) && (0 <= c) && (c < COLS))
	            {
	                // check if previously visited
	                if ((Maze[r][c] & N) && (Maze[r][c] & E) && (Maze[r][c] & S) && (Maze[r][c] & W))
	                {
	                    // not visited, so add to possible next cells
	                    next[j][0] = r;
	                    next[j][1] = c;
	                    j++;
	                }
	            }
	        }
	        
	        if (j > 0)
	        {
	            // current cell has one or more unvisited neighbors, so choose one at random  
	            // and knock down the wall between it and the current cell
	            i = randint(0, j-1);

	            if ((next[i][0] - curr[0]) == 0)    // next on same row
	            {
	                r = next[i][0];
	                if (next[i][1] > curr[1])       // move east
	                {
	                    c = curr[1];
	                    Maze[r][c] &= ~E;           // clear E wall
	                    c = next[i][1];
	                    Maze[r][c] &= ~W;           // clear W wall
	                }
	                else                            // move west
	                {
	                    c = curr[1];
	                    Maze[r][c] &= ~W;           // clear W wall
	                    c = next[i][1];
	                    Maze[r][c] &= ~E;           // clear E wall
	                }
	            }
	            else                                // next on same column
	            {
	                c = next[i][1]
	                if (next[i][0] > curr[0])       // move south    
	                {
	                    r = curr[0];
	                    Maze[r][c] &= ~S;           // clear S wall
	                    r = next[i][0];
	                    Maze[r][c] &= ~N;           // clear N wall
	                }
	                else                            // move north
	                {
	                    r = curr[0];
	                    Maze[r][c] &= ~N;           // clear N wall
	                    r = next[i][0];
	                    Maze[r][c] &= ~S;           // clear S wall
	                }
	            }

	            tos++;                              // push current cell location
	            Stack[tos][0] = curr[0];
	            Stack[tos][1] = curr[1];

	            curr[0] = next[i][0];               // make next cell the current cell
	            curr[1] = next[i][1];

	            visited++;                          // increment count of visited cells
	        }
	        else
	        {
	            // reached dead end, backtrack
	            // pop the most recent cell from the cell stack            
	            // and make it the current cell
	            curr[0] = Stack[tos][0];
	            curr[1] = Stack[tos][1];
	            tos--;
	        }
	    }
	}

//	 draw result in a separate window, call "open_window()" before this
	def export_maze()
	{
	/*
	    def i, j;
		def Rooms = new Array(ROWS), exit = null;
		
		// populate 2D array of room objects
	    for (i = 0; i < ROWS; i++)
	    {
			Rooms[i] = new Array(COLS);
	        for (j = 0; j < COLS; j++)
	        {
	        	def atEnd = (i == COLS-1 && j == ROWS-1);
	        	def room = new Create(p2pmud.vars.admin, atEnd ? "The End" :"Twisty Maze", "Room").execute();
	        	Rooms[i][j] = room;
	        	DoSetProp(room, "description", atEnd ? "You breath a sigh of relief as you reach the end of the dungeon!! Congrats, you made it!!" +EOL +EOL + writeout_maze(): "You are in a twisty little maze, where all the rooms look exactly alike..  How cliche!");
	        	DoSetProp(room, "imageUrl", "http://p2pmud.sourceforge.net/maze.jpg"); //"http://www.conescoinc.com/pics/hogle/maze.JPG");
			}
		}	
				
	    for (i = 0; i < ROWS; i++)
	    {
	        for (j = 0; j < COLS; j++)
	        {
	            if (!(Maze[i][j] & N) && i > 0) {
	            	exit = new Create(Rooms[i][j], "north", "Exit").execute();
	            	DoSetProp(exit, "imageUrl","http://p2pmud.sourceforge.net/north.jpg");
	            	DoSetProp(exit, "x", "277");
	            	DoSetProp(exit, "y", "98");
	            	new Link(p2pmud.vars.admin, exit, Rooms[i - 1][j]).execute();
	            }
	            if (!(Maze[i][j] & S) && i < ROWS-1) {
	            	exit = new Create(Rooms[i][j], "south", "Exit").execute();
	            	DoSetProp(exit, "imageUrl","http://p2pmud.sourceforge.net/south.jpg");
	            	DoSetProp(exit, "x", "277");
	            	DoSetProp(exit, "y", "251");
	            	new Link(p2pmud.vars.admin, exit, Rooms[i + 1][j]).execute();
	            }
	            if (!(Maze[i][j] & W) && j > 0) {
	            	exit = new Create(Rooms[i][j], "west", "Exit").execute();
	            	DoSetProp(exit, "imageUrl","http://p2pmud.sourceforge.net/west.jpg");
	            	DoSetProp(exit, "x", "75");
	            	DoSetProp(exit, "y", "142");
	            	new Link(p2pmud.vars.admin, exit, Rooms[i][j - 1]).execute();
	            }
	            if (!(Maze[i][j] & E) && j < COLS-1) {
	            	exit = new Create(Rooms[i][j], "east", "Exit").execute();
	            	DoSetProp(exit, "x", "519");
	            	DoSetProp(exit, "y", "143");
	            	DoSetProp(exit, "imageUrl","http://p2pmud.sourceforge.net/east.jpg");
	            	new Link(p2pmud.vars.admin, exit, Rooms[i][j + 1]).execute();
	            }
	        }
	    }
	    return Rooms;
	    */
	}

	def populate(Rooms) {
		/*
	    def i, j;
	    for (i = 0; i < ROWS; i++)
	    {
	        for (j = 0; j < COLS; j++)
	        {
				def r = randint(0, 3);
				if (r == 0) createRandomMonster(Rooms[i][j]);
				else if (r == 1) createRandomTreasure(Rooms[i][j]);
			}		
		}	
		
		plantMaiden(Rooms[randint(1, ROWS-1)][randint(1, COLS-1)]);
		*/
	}

	def createRandomMonster(room) {
		/*
		def monsters = [
			[ 'orc', 'http://www.netmoon.com/warcraft/image/orc.gif'],
			[ 'rat', 'http://www.imras.com/images/large-rat.gif'],
			[ 'skeleton', 'http://www.muranskychiropractic.com/graphics/running-skeleton.gif'],
			[ 'dragon', 'http://www.masterman.phila.k12.pa.us/about/images/dragon.gif'],
			[ 'minotaur', 'http://www.pcdoct.com/images/minotaur.gif'],
			[ 'slime', 'http://www.ericjorgensen.com/pics/slime.gif'],
			[ 'troll', 'http://www.ffcompendium.com/~Skylark/ff9/Troll.gif']
			];
		def growth = [
			[ 'small', 10, 50 ],
			[ 'smelly', 25, 75 ],
			[ 'creepy', 40, 100 ],
			[ 'hairy', 55, 125 ],
			[ 'angry', 75, 150 ],
			[ 'huge', 100, 175 ],
			[ 'fierce', 150, 200 ],
			[ 'epic', 250,  225 ]
			];
					
		def it = monsters[randint(0, monsters.length - 1)];
		def sz = growth[randint(1, 4) + randint(1, 4) - 1];
		
		def mon = new Create(room, sz[0] + " " + it[0], "Monster").execute();
		
		DoSetProp(mon, "imageUrl", it[1]);
		DoSetProp(mon, "h", sz[2]);
		DoSetProp(mon, "x", randint(100, 400));
		DoSetProp(mon, "y", randint(240, 400) - sz[2]);
		
		new RollStat(p2pmud.vars.admin, mon, sz[1]).execute();
		return mon;
		*/
	}

	def createRandomTreasure(room) {
		/*
		def treasure = [
			{ name : 'shiny apple', species : 'Food', imageUrl: 'http://fp.enter.net/~rburk/images/apple.gif' },
			{ name : 'roast ham', species : 'Food', foodValue : 20, imageUrl: 'http://www003.upp.so-net.ne.jp/ootaya/s_ham.GIF' },
			
			{ name : 'rusty sword', species : 'Weapon', damage : 10, imageUrl: 'http://kirth.puddleby.info/pictures/dagger.gif' },
			{ name : 'sharp dagger', species : 'Weapon', damage : 15, imageUrl: 'http://kirth.puddleby.info/pictures/dagger.gif' },
			{ name : 'magic wand', species : 'Weapon', damage : 25, imageUrl: 'http://www.enchantedlearning.com/wgifs/Wand.GIF' },
			{ name : 'iron mace', species : 'Weapon', damage : 20, imageUrl: 'http://www.everquest-online.com/content/images/items/item_578.gif' },
			
			{ name : 'golden idol', species : 'Thing', imageUrl : 'http://www.everquest-online.com/content/images/items/item_893.gif' },
			{ name : 'burning torch', species : 'Thing', imageUrl : 'http://www.hallmarktrack.org/graphics/torch.gif' },
			{ name : 'magic goblet', species : 'Thing', imageUrl:'http://www1.linkclub.or.jp/~clubey/wolf.gifs/pc%20graphics/pc.chalice.gif' },
			{ name : 'giant ruby', species : 'Thing', imageUrl:'http://www.sawadee.com/thailand/gems/ruby.gif' }
		];
		
		def it = treasure[randint(0, treasure.length - 1)];
		def tr = new Create(room, it.name, it.species).execute();
		if ('foodValue' in it) DoSetProp(tr, 'foodValue', it.foodValue);
		if ('damage' in it) DoSetProp(tr, 'damage', it.damage);
		if ('imageUrl' in it) DoSetProp(tr, 'imageUrl', it.imageUrl);
		DoSetProp(tr, "x", randint(100, 400));
		DoSetProp(tr, "y", randint(240, 380));
		return tr;
		*/
	}

	def plantMaiden(room) {
		/*
		def maid = new Create(room, "fairy maiden", 'Thing').execute();
		DoSetProp(maid, "imageUrl", "http://homepage.mac.com/nexyjo/NexysCocoon/vsdoll02shaded.gif");
		DoSetProp(maid, "h", 150);
		DoSetProp(maid, "x", randint(100, 400));
		DoSetProp(maid, "y", randint(240, 380) - 150);
		DoSetProp(maid, "description", "You see before you a beautiful fairy maiden.  A gallant man, such as yourself, would be a fool not to rescue her!");
		return maid;
		*/
	}
		
/*	 draw result in a separate window, call "open_window()" before this
	def writeout_maze()
	{
	    def i, j, k, maze = "<pre>";

	    for (i = 0; i < ROWS; i++)
	    {
	        for (k = 1; k <= 2; k++)
	        {
	            for (j = 0; j < COLS; j++)
	            {
	                if (k == 1)              // upper corners and walls
	                {
	                    if (Maze[i][j] & N)
	                        maze += "+---";
	                    else
	                        maze += "+   ";

	                    if ((j + 1) == COLS)
	                        maze += "+";

	                }
	                else if (k == 2)         // center walls and open areas
	                {
	                    if (Maze[i][j] & W)
	                        maze += "|   ";
	                    else
	                        maze += "    ";

	                    if ((j + 1) == COLS)
	                        maze += "|";

	                }
	            }
	            maze += EOL;
	        }
	    }
	    for (j = 0; j < COLS; j++)          // bottom-most corners and walls
	        maze += "+---";

	    maze += "+" + EOL;  // last corner on bottom
	    return maze + "</pre>";
	}
	*/
	def convertTo3DBlocks() {
		blockRows = ROWS * cellsz
		blockCols = COLS * cellsz
		def blocks = new Object[blockRows]
		for (def i = 0; i < ROWS * cellsz; ++i) {
			blocks[i] = new Object[blockCols]
			for (def j = 0; j < COLS * cellsz; ++j) {
				blocks[i][j] = 'X'
			}
		}
		
		for (def i = 0; i < ROWS; ++i) {
			for (def j = 0; j < COLS; ++j) {
				carveRoom(blocks, i, j, Maze[i][j])
			}
		}

		// convert some halls to secret passages to be sneaky
		for (def i = 0; i < ROWS; ++i) {
			for (def j = 0; j < COLS; ++j) {
				if (randint(1, 8) == 1) carveSecret(blocks, i, j, Maze[i][j])
			}
		}
		
		return blocks
	}
	def carveRoom(blocks, i, j, room) {
		for (def x = hallsize; x < cellsz - hallsize; ++x) {
			for (def y = hallsize; y < cellsz - hallsize; ++y) {
				blocks[i * cellsz + x][j * cellsz + y] = ' '
			}
		}
		
		def half = Math.floor(cellsz / 2) as Integer
		def halfhall = hallsize == 1 ? 0 : Math.round(hallsize/2) as Integer 
		if (!(room & W)) {
			for (def y = 0; y < hallsize; ++y)
				blocks[i * cellsz + half][j * cellsz + y] = '.'
		}
		if (!(room & E)) {
			for (def y = 0; y < hallsize; ++y)
				blocks[i * cellsz + half][j * cellsz + roomsize + hallsize + y] = '.'
			blocks[i * cellsz + half][j * cellsz + roomsize + hallsize + halfhall ] = 'e'
		}
		if (!(room & N)) {
			for (def x = 0; x < hallsize; ++x)
				blocks[i * cellsz + x][j * cellsz + half] = '.'
		}
		if (!(room & S)) {
			for (def x = 0; x < hallsize; ++x)
				blocks[i * cellsz + roomsize + hallsize + x][j * cellsz + half] = '.'
			blocks[i * cellsz + roomsize + hallsize + halfhall ][j * cellsz + half] = 's'
		}
	}
	
	def carveSecret(blocks, i, j, room) {
		def half = Math.floor(cellsz / 2) as Integer, halfhall = Math.floor(hallsize/2) as Integer
		while (true) {
			def secret = randint(0, 100) % 4
			if (secret  == 0 && !(room & W)) {
				for (def y = -hallsize; y < hallsize; ++y)
					blocks[i * cellsz + half][j * cellsz + y] = 'z'
			}
			if (secret == 1 && !(room & E)) {
				for (def y = -hallsize; y < hallsize; ++y)
					blocks[i * cellsz + half][j * cellsz + roomsize + hallsize + y + half - 1] = 'z'
			}
			if (secret  == 2 && !(room & N)) {
				for (def x = -hallsize; x < hallsize; ++x)
					blocks[i * cellsz + x][j * cellsz + half] = 'z'
			}
			if (secret  ==13 && !(room & S)) {
				def cell = randint(1, 10) == 1 ? 'z' : '.'
				for (def x = -hallsize; x < hallsize; ++x)
					blocks[i * cellsz + roomsize + hallsize + x + half - 1][j * cellsz + half] = 'z'
			}
			if (randint(0, 100) % 3 != 0) return
		}
	}
	
	def showDungeonViewer(blocks) {
		def f
		new SwingBuilder().build {
			def field = {lbl, key ->
				label(text: lbl)
				//fields[key] = textField(actionPerformed: {setprop(key)}, focusLost: {setprop(key)}, text: p[key], constraints: 'wrap, growx')
			}
				f = frame(title: 'Plexus', windowClosing: {System.exit(0)}, layout: new MigLayout("wrap $blockCols"), pack: true, show: true) {
			    for (i = 0; i < blockRows; i++) {
			        for (j = 0; j < blockCols; j++) {
		            	label(text: blocks[i][j])
		            }
		        }
				//button(text: "Exit", actionPerformed: {f.dispose(); System.exit(0)})
			}
			f.size = [500, (int)f.size.height] as Dimension
		}
	}


	def static void main(args) {
		def r = 8, c = 8
		if (args) {
			r = args[0]
			c = args[1]
		}
		println "Generating maze rows: $r cols: $c"
		def dungeon = new Dungeon(6, 6, 3, 1) // new Dungeon(r, c)
	    dungeon.generate_maze();
		
		def blocks = dungeon.convertTo3DBlocks()
		dungeon.showDungeonViewer(blocks)
	}
}