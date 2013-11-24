irccasino
=========
### What is irccasino? ###
irccasino is a Java package that implements casino games for IRC using the [PircBotX][1] library. A standalone bot is included, but the package can also be added to any existing PircBotX-based project. For more information, visit the [project wiki][4] on GitHub or join us on [Freenode][5] in `##casino` and `##holdem`.

### Games ###
* Blackjack
* No Limit Texas Hold'em Poker

### Requirements ###
1. [Java][2] 6 or higher
2. [PircBotX 1.9][3]

Standalone Setup
================
### Create project ###
1. Download the [PircBotX][3] library (the JAR package). 
2. Download irccasino and create a new project for it.
3. Add the PircBotX library to the build/compile path for the project.
4. Set the project to run *CasinoBot.java*.

### Configuration ###
1.  Copy the *.help* files and *strlib.txt* to the project run directory.
2.  Create *irccasino.conf* in the project run directory with the following contents:

		nick=bot nick
		password=bot password (optional)
		network=IRC network (e.g. chat.freenode.net)
		channel=IRC channels to auto-join (comma delimited)

### Run standalone bot ###
1. Run the project.
2. Give the bot Op status in the channels that will run the games.
3. While as channel Op in those channels, type the command ".texaspoker" or ".blackjack" to start that game in the channel.

[1]: http://code.google.com/p/pircbotx/ "PircBotX"
[2]: http://www.oracle.com/technetwork/java/javase/downloads/index.html "Java SE"
[3]: http://repo1.maven.org/maven2/org/pircbotx/pircbotx/1.9/pircbotx-1.9.jar "pircbotx-1.9"
[4]: https://github.com/brrr2/irccasino/wiki "Wiki"
[5]: https://webchat.freenode.net "Freenode"