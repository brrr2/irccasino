irccasino
=========
### What is irccasino? ###
irccasino is a Java package that implements casino games for IRC using the 
[PircBotX IRC library][1] and [SQLite JDBC Driver][9]. A standalone bot is 
included, but the package can also be added to any existing PircBotX-based 
project. For more information, visit the [project wiki][4] on GitHub or join 
us on [Freenode][5] in `##casino` and `##holdem`.

### Games ###
* Blackjack
* No Limit Texas Hold'em Poker
* No Limit Texas Hold'em Tournament

### Requirements ###
1. [Java][2] 7 or higher
2. [PircBotX 1.9][3]
3. [SQLite JDBC Driver][10]

Standalone Setup
================
### Create project ###
1. Create a new project using your preferred IDE.
2. Download the *irccasino* package into the source directory of the project.
3. Download the [PircBotX][3] IRC library (JAR package).
4. Download the [SQLite JDBC][10] driver (JAR package).
4. Add both JAR packages to the build/compile path for the project.
5. Set the project to run *CasinoBot.java*.

### Configuration ###
1.  Copy the *.help* files and *strlib.txt* to the project run directory.
2.  Create *irccasino.conf* in the project run directory with the following contents:

        nick=bot nick
        password=bot password (optional)
        network=IRC network (e.g. chat.freenode.net)
        channel=IRC channels to auto-join (comma delimited)
        bjchannel=IRC channels to auto-start Blackjack (comma delimited)
        tpchannel=IRC channels to auto-start Texas Hold'em (comma delimited)
        ttchannel=IRC channels to auto-start Texas Hold'em Tournament (comma delimited)
                
### Run standalone bot ###
1. Run the project.
2. Give the bot Op status in the channels that will run the games.
3. While as channel Op in those channels, type the command `.texaspoker`, `.texastourney` or `.blackjack` to start that game in the channel.
4. Other useful Op commands are `.botquit`, `.reboot`, `.endgame`, and `.games`.

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