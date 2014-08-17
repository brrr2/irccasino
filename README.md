irccasino
=========
#### What is irccasino?
irccasino is a Java package that implements casino games for IRC using the 
[PircBotX IRC library][1] and [Xerial SQLite JDBC driver][9]. A standalone bot 
is included, but the package can also be added to any existing PircBotX-based 
project. For more information, visit the [project wiki][4] on GitHub or join 
us on [Freenode][5] in `##casino` and `##holdem`.

#### Games
* Blackjack
* No Limit Texas Hold'em Poker
* No Limit Texas Hold'em Tournament

#### Requirements
1. [Java][2] 7 or higher
2. [PircBotX 1.9 IRC library][3]
3. [Xerial SQLite JDBC driver][10]

IDE Quick Setup
===============
#### Create Project
1. Create a new project `MyCasinoBot` using your preferred IDE.
2. Clone the `irccasino` package into the source directory of the project.
3. Download the [PircBotX IRC library][3] and [Xerial SQLite JDBC driver][10] 
   (JAR packages).
4. Add both JAR packages to the build/compile path for the project.
5. Set the project to run `CasinoBot.java`.

#### Configuration
1. Copy all `.help`, `.txt`, and `.conf` files from the `irccasino` package 
   into the project root directory.
2. In the project root directory, edit the values of `casinobot.conf` as 
   necessary.
                
#### Running CasinoBot
1. Run the project.
2. Give the bot Op status in the channels that will run the games.
3. Use the Op commands `.texaspoker`, `.texastourney` or `.blackjack` to start 
   a game in a channel in which the bot is joined.
4. Other useful Op commands are `.botquit`, `.reboot`, `.endgame`, and `.games`.

Command Line Quick Setup
================================
#### Create Project
1. Create a directory `MyCasinoBot` and the subdirectories `MyCasinoBot/src` and
   `MyCasinoBot/bin`.
2. Clone the `irccasino` package into `MyCasinoBot/src/irccasino`. 
3. Download the [PircBotX IRC library][3] and [Xerial SQLite JDBC driver][10] 
   into `MyCasinoBot/bin/` (JAR packages).
4. From `MyCasinoBot`, compile the source using the command: 

        # Linux/OS X
        javac -d bin/ -cp bin/sqlite-jdbc-3.7.2.jar:bin/pircbotx-1.9.jar src/irccasino/*.java src/irccasino/*/*.java
        # Windows
        javac -d bin/ -cp bin/sqlite-jdbc-3.7.2.jar;bin/pircbotx-1.9.jar src/irccasino/*.java src/irccasino/*/*.java

#### Configuration
1. Copy all `.help`, `.txt`, and `.conf` files from the `irccasino` package 
   into `MyCasinoBot`.
2. In `MyCasinoBot`, edit the values of `casinobot.conf` as necessary.

#### Running CasinoBot
1. From `MyCasinoBot`, run the command: 

        # Linux/OS X
        java -cp bin/:bin/sqlite-jdbc-3.7.2.jar:bin/pircbotx-1.9.jar irccasino.CasinoBot
        # Windows
        java -cp bin/;bin/sqlite-jdbc-3.7.2.jar;bin/pircbotx-1.9.jar irccasino.CasinoBot

2. Give the bot Op status in the channels that will run the games.
3. Use the Op commands `.texaspoker`, `.texastourney` or `.blackjack` to start 
   a game in a channel in which the bot is joined.
4. Other useful Op commands are `.botquit`, `.reboot`, `.endgame`, and `.games`.

Upgrading
=========
1. When upgrading to a new version, be sure to use the library and 
   configuration files from the new version (`.help` and `.txt`).
2. Version 0.3.9 and newer uses a SQLite database (`stats.sqlite3`) to store 
   game data. Use the `.migrate` command from within any game to migrate old 
   game data from `players.txt` and `housestats.txt`. The migration process 
   will not overwrite any existing data in the database.

Contributors
============
[brrr2][6] (Main Author)  
[Yky][7]  
[RantingHuman][8]

[1]: http://code.google.com/p/pircbotx/ "PircBotX"
[2]: http://www.oracle.com/technetwork/java/javase/downloads/index.html "Java SE"
[3]: http://repo1.maven.org/maven2/org/pircbotx/pircbotx/1.9/pircbotx-1.9.jar "pircbotx-1.9.jar"
[4]: https://github.com/brrr2/irccasino/wiki "Wiki"
[5]: https://webchat.freenode.net/?channels=##casino,##holdem "Freenode"
[6]: https://github.com/brrr2 "brrr2"
[7]: https://github.com/Yky "Yky"
[8]: https://github.com/RantingHuman "RantingHuman"
[9]: https://bitbucket.org/xerial/sqlite-jdbc "sqlite-jdbc"
[10]: https://bitbucket.org/xerial/sqlite-jdbc/downloads/sqlite-jdbc-3.7.2.jar "sqlite-jdbc-3.7.2.jar"