/*
	Copyright (C) 2013 Yizhe Shen <brrr@live.ca>
	
	This file is part of irccasino.

    irccasino is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    irccasino is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with irccasino.  If not, see <http://www.gnu.org/licenses/>.
*/
package irccasino;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import org.pircbotx.*;

public abstract class CardGame{
    /* Start round task to be performed after post-start waiting period */
	public static class StartRoundTask extends TimerTask{
		CardGame game;
		public StartRoundTask(CardGame g){
			game = g;
		}
		@Override
		public void run(){
			game.startRound();
		}
	}
    /* Idle task for removing idle players */
	public static class IdleOutTask extends TimerTask {
		private Player player;
		private CardGame game;
		public IdleOutTask(Player p, CardGame g) {
			player = p;
			game = g;
		}

		@Override
		public void run() {
			if (game.isInProgress() && player == game.getCurrentPlayer()) {
				game.bot.sendMessage(game.channel, player.getNickStr()
						+ " has wasted precious time and idled out.");
				game.leave(player);
			}
		}
	}
    /* Respawn task for giving loans after bankruptcies */
	public static class RespawnTask extends TimerTask {
		Player player;
		CardGame game;
		public RespawnTask(Player p, CardGame g) {
			player = p;
			game = g;
		}
		@Override
		public void run() {
			ArrayList<RespawnTask> tasks = game.getRespawnTasks();
			player.setCash(game.getNewCash());
			player.addDebt(game.getNewCash());
			game.bot.sendMessage(game.channel, player.getNickStr() + " has been loaned $"
							+ formatNumber(game.getNewCash()) + ". Please bet responsibly.");
			game.savePlayerData(player);
            game.removeBlacklisted(player);
            tasks.remove(this);
		}
	}

    protected PircBotX bot; //bot handling the game
    protected ExampleBot parentListener; //ListenerAdapter that is receiving commands
    protected Channel channel; //channel where game is being played
    protected String gameName, iniFile, helpFile;
    protected ArrayList<Player> joined, blacklist, waitlist;
    protected CardDeck deck;
    protected Player currentPlayer;
    protected boolean inProgress, betting;
    protected Timer idleOutTimer, startRoundTimer, respawnTimer;
    protected IdleOutTask idleOutTask;
    protected int idleOutTime, respawnTime, newcash, maxPlayers;
    private StartRoundTask startRoundTask;
    private ArrayList<RespawnTask> respawnTasks;
    
    /**
     * Creates a generic CardGame.
     * Not to be directly instantiated.
     * 
     * @param parent The bot that creates an instance of this class
     * @param gameChannel The IRC channel in which the game is to be run.
     * @param eb The ListenerAdapter that is listening for commands for this game
     */
    public CardGame (PircBotX parent, Channel gameChannel, ExampleBot eb){
        bot = parent;
        channel = gameChannel;
        parentListener = eb;
        joined = new ArrayList<Player>();
        blacklist = new ArrayList<Player>();
        waitlist = new ArrayList<Player>();
        respawnTasks = new ArrayList<RespawnTask>();
        idleOutTimer = new Timer("IdleOutTimer");
        startRoundTimer = new Timer("StartRoundTimer");
        respawnTimer = new Timer("RespawnTimer");
        startRoundTask = null;
        idleOutTask = null;
        inProgress = false;
        betting = false;
        currentPlayer = null;
        checkPlayerFile();
    }
    
    /* Methods that process IRC events */
    /**
     * Processes commands in the channel where the game is running.
     * 
     * @param user IRC user who issued the command.
     * @param command The command that was issued.
     * @param params A list of parameters that were passed along.
     */
    public void processCommand(User user, String command, String[] params){
        String nick = user.getNick();
        
        /* Common commands for all games that can be called at anytime or have 
         * the same permissions. */
        if (command.equals("leave") || command.equals("quit") || command.equals("l") || command.equals("q")){
            leave(nick);
        } else if (command.equals("cash")) {
            if (params.length > 0){
                showPlayerCash(params[0]);
            } else {
                showPlayerCash(nick);
            }
        } else if (command.equals("netcash") || command.equals("net")) {
            if (params.length > 0){
                showPlayerNetCash(params[0]);
            } else {
                showPlayerNetCash(nick);
            }
        } else if (command.equals("debt")) {
            if (params.length > 0){
                showPlayerDebt(params[0]);
            } else {
                showPlayerDebt(nick);
            }
        } else if (command.equals("bankrupts")) {
            if (params.length > 0){
                showPlayerBankrupts(params[0]);
            } else {
                showPlayerBankrupts(nick);
            }
        } else if (command.equals("rounds")) {
            if (params.length > 0){
                showPlayerRounds(params[0]);
            } else {
                showPlayerRounds(nick);
            }
        } else if (command.equals("player") || command.equals("p")){
            if (params.length > 0){
                showPlayerAllStats(params[0]);
            } else {
                showPlayerAllStats(nick);
            }
        } else if (command.equals("paydebt") ) {
            if (!isJoined(nick)) {
                infoNotJoined(nick);
            } else if (isInProgress()) {
                infoWaitRoundEnd(nick);
            } else {
                if (params.length > 0){
                    try {
                        payPlayerDebt(nick, Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("waitlist")) {
            showWaitlist();
        } else if (command.equals("blacklist")) {
            showBlacklist();
        } else if (command.equals("top")) {
            if (isInProgress()) {
                infoWaitRoundEnd(nick);
            } else {
                if (params.length > 1){
                    try {
                        showTopPlayers(params[1].toLowerCase(), Integer.parseInt(params[0]));
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else if (params.length == 1){
                    try {
                        showTopPlayers("cash", Integer.parseInt(params[0]));
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    showTopPlayers("cash", 5);
                }
            }
        } else if (command.equals("simple")) {
            if (!isJoined(nick)) {
                infoNotJoined(nick);
            } else {
                togglePlayerSimple(nick);
            }
        } else if (command.equals("stats")){
            if (isInProgress()) {
                infoWaitRoundEnd(nick);
            } else {
                showGameStats();
            }
        } else if (command.equals("grules")) {
            infoGameRules(nick);
        } else if (command.equals("ghelp")) {
            if (params.length == 0){
                infoGameHelp(nick);
            } else {
                infoGameCommandHelp(nick, params[0].toLowerCase());
            }
        } else if (command.equals("gcommands")) {
            infoGameCommands(nick);
        } else if (command.equals("game")) {
            showGameName();
        // Op Commands
        } else if (command.equals("fl") || command.equals("fq") || command.equals("fquit") || command.equals("fleave")){
            if (!channel.isOp(user)) {
                infoOpsOnly(nick);
            } else {
                if (params.length > 0){
                    leave(params[0]);
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("cards") || command.equals("discards")) {
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        int num = Integer.parseInt(params[0]);
                        if (command.equals("cards") && deck.getNumberCards() > 0) {
                            infoDeckCards(nick, 'c', num);
                        } else if (command.equals("discards") && deck.getNumberDiscards() > 0) {
                            infoDeckCards(nick, 'd', num);
                        } else {
                            bot.sendNotice(nick, "Empty!");
                        }
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("set")){
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 1){
                    try {
                        setSetting(params);
                        showUpdateSetting(params[0]);
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("get")) {
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        showSetting(params[0], getSetting(params[0]));
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        }
    }
    
    /**
     * Processes a user join event in the channel where the game is running.
     * 
     * @param user The IRC user who has joined.
     */
    public void processJoin(User user){
        String nick = user.getNick();
    	if (loadPlayerStat(nick, "exists") != 1){
    		infoNewNick(nick);
    	}
    }
    
    /**
     * Processes a user quit or part event in the channel where the game is running.
     * 
     * @param user The IRC user who has quit or parted.
     */
    public void processQuit(User user){
        String nick = user.getNick();
		if (isJoined(nick) || isWaitlisted(nick)){
			leave(nick);
		}
    }
    
    /**
     * Process a user change nick event in the channel where the game is running.
     * 
     * @param user The IRC user who has changed nicks.
     * @param oldNick The old nick of the user.
     * @param newNick The new nick of the user.
     */
    public void processNickChange(User user, String oldNick, String newNick){
    	String hostmask = user.getHostmask();
    	if (isJoined(oldNick) || isWaitlisted(oldNick)){
    		infoNickChange(newNick);
    		if (isJoined(oldNick)){
    			bot.deVoice(channel, user);
		    	leave(oldNick);
	    	} else if(isWaitlisted(oldNick)){
				removeWaitlisted(oldNick);
	    	}
    		join(newNick, hostmask);
    	}
    }
    
    /* 
     * Accessor methods 
     * Returns or sets object properties.
     */
    public void setIdleOutTime(int value){
    	idleOutTime = value;
    }
    public int getIdleOutTime(){
    	return idleOutTime;
    }
    public void setRespawnTime(int value){
    	respawnTime = value;
    }
    public int getRespawnTime(){
    	return respawnTime;
    }
    public void setNewCash(int value){
    	newcash = value;
    }
    public int getNewCash(){
    	return newcash;
    }
    public Channel getChannel(){
        return channel;
    }
    public String getGameName(){
        return gameName;
    }
    
    /* 
     * Game management methods
     * These methods control the game flow. 
     */
    abstract public void startRound();
    abstract public void continueRound();
    abstract public void endRound();
    abstract public void endGame();
    abstract public void resetGame();
    abstract public void leave(String nick);
    abstract public void setSetting(String[] params);
    abstract public String getSetting(String param);
    abstract public void loadSettings();
    abstract public void saveSettings();
	public void join(String nick, String hostmask) {
        if (getNumberJoined() == maxPlayers){
            infoMaxPlayers(nick);
        } else if (isJoined(nick)) {
			infoAlreadyJoined(nick);
		} else if (isBlacklisted(nick)) {
			infoBlacklisted(nick);
		} else if (isInProgress()) {
			if (isWaitlisted(nick)) {
				infoAlreadyWaitlisted(nick);
			} else {
				addWaitlistPlayer(nick, hostmask);
			}
		} else {
			addPlayer(nick, hostmask);
		}
	}
	public void leave(Player p){
		leave(p.getNick());
	}
    public void mergeWaitlist(){
        for (int ctr = 0; ctr < waitlist.size(); ctr++){
            addPlayer(getWaitlisted(ctr));
        }
        waitlist.clear();
    }
    public void setInProgress(boolean b){
        inProgress = b;
    }
    public boolean isInProgress(){
        return inProgress;
    }
    public void setBetting(boolean b){
        betting = b;
    }
    public boolean isBetting(){
        return betting;
    }
    public void setRespawnTask(Player p) {
		RespawnTask task = new RespawnTask(p, this);
		respawnTimer.schedule(task, respawnTime*1000);
		respawnTasks.add(task);
	}
	public void cancelRespawnTasks() {
		Player p;
        for (int ctr = 0; ctr < respawnTasks.size(); ctr++) {
            respawnTasks.get(ctr).cancel();
        }
        respawnTasks.clear();
        respawnTimer.purge();
        // Fast-track loans
		for (int ctr = 0; ctr < getNumberBlacklisted(); ctr++){
			p = getBlacklisted(ctr);
			p.setCash(getNewCash());
			p.addDebt(getNewCash());
            savePlayerData(p);
		}
	}
	public ArrayList<RespawnTask> getRespawnTasks() {
		return respawnTasks;
	}
    public void setStartRoundTask(){
        startRoundTask = new StartRoundTask(this);
    	startRoundTimer.schedule(startRoundTask, 5000);
    }
    public void cancelStartRoundTask(){
        if (startRoundTask != null){
            startRoundTask.cancel();
            startRoundTimer.purge();
        }
    }
	public void setIdleOutTask() {
        idleOutTask = new IdleOutTask(currentPlayer, this);
		idleOutTimer.schedule(idleOutTask, idleOutTime*1000);
	}
    public void cancelIdleOutTask() {
        if (idleOutTask != null){
            idleOutTask.cancel();
            idleOutTimer.purge();
        }
    }
    public boolean isOpCommandAllowed(User user, String nick){
		if (!channel.isOp(user)) {
			infoOpsOnly(nick);
		} else if (isInProgress()) {
			infoWaitRoundEnd(nick);
		} else {
            return true;
        }
        return false;
	}
    
    /* 
     * Player management methods 
     * Controls joining, leaving, waitlisting, and bankruptcies. Also includes
     * toggling of simple, paying debt.
     */
    abstract public void addPlayer(String nick, String hostmask);
    abstract public void addWaitlistPlayer(String nick, String hostmask);
	public void addPlayer(Player p){
		User user = findUser(p.getNick());
		joined.add(p);
		loadPlayerData(p);
		if (user != null){
			bot.voice(channel, user);
		}
		showJoin(p);
	}
    public boolean isJoined(String nick){
        return (findJoined(nick) != null);
    }
    public boolean isJoined(Player p){
        return joined.contains(p);
    }
    public boolean isWaitlisted(String nick){
    	return (findWaitlisted(nick) != null);
    }
    public boolean isWaitlisted(Player p){
    	return waitlist.contains(p);
    }
    public boolean isBlacklisted(String nick){
    	return (findBlacklisted(nick) != null);
    }
    public boolean isBlacklisted(Player p){
    	return blacklist.contains(p);
    }
    public void removeJoined(String nick){
        Player p = findJoined(nick);
        removeJoined(p);
    }
    public void removeJoined(Player p){
    	User user = findUser(p.getNick());
    	joined.remove(p);
    	savePlayerData(p);
    	if (user != null){
    		bot.deVoice(channel, user);
    	}
    	showLeave(p);
    }
    public void removeWaitlisted(String nick){
    	Player p = findWaitlisted(nick);
    	removeWaitlisted(p);
    }
    public void removeWaitlisted(Player p){
    	waitlist.remove(p);
    }
    public void removeBlacklisted(String nick){
    	Player p = findBlacklisted(nick);
    	removeBlacklisted(p);
    }
    public void removeBlacklisted(Player p){
    	blacklist.remove(p);
    }
    public User findUser(String nick){
        Set<User> users = bot.getUsers(channel);
        User user;
        Iterator<User> it = users.iterator();
        while(it.hasNext()){
        	user = it.next();
            if (user.getNick().toLowerCase().equals(nick.toLowerCase())){
                return user;
            }
        }
        return null;
    }
    public Player findJoined(String nick){
    	for (int ctr=0; ctr< getNumberJoined(); ctr++){
    		Player p = getJoined(ctr);
            if (p.getNick().toLowerCase().equals(nick.toLowerCase())){
                return p;
            }  
        }
    	return null;
    }
    public Player findWaitlisted(String nick){
    	for (int ctr=0; ctr< getNumberWaitlisted(); ctr++){
    		Player p = getWaitlisted(ctr);
    		if (p.getNick().toLowerCase().equals(nick.toLowerCase())){
                return p;
            }  
        }
    	return null;
    }
    public Player findBlacklisted(String nick){
    	for (int ctr=0; ctr< getNumberBlacklisted(); ctr++){
    		Player p = getBlacklisted(ctr);
    		if (p.getNick().toLowerCase().equals(nick.toLowerCase())){
            	return p;
            }  
        }
    	return null;
    }
    public Player getJoined(int num){
        return joined.get(num);
    }
    public Player getWaitlisted(int num){
    	return waitlist.get(num);
    }
    public Player getBlacklisted(int num){
    	return blacklist.get(num);
    }
    public int getNumberJoined(){
        return joined.size();
    }
    public int getNumberWaitlisted(){
    	return waitlist.size();
    }
    public int getNumberBlacklisted(){
    	return blacklist.size();
    }
    public Player getCurrentPlayer(){
        return currentPlayer;
    }
    public int getJoinedIndex(Player p){
    	return joined.indexOf(p);
    }
    public Player getNextPlayer(){
        int index = joined.indexOf(currentPlayer);
        if (index + 1 < getNumberJoined()){
            return joined.get(index+1);
        } else {
            return null;
        }
    }
    public void togglePlayerSimple(String nick){
        Player p = findJoined(nick);
        p.setSimple(!p.isSimple());
        if (p.isSimple()){
            bot.sendNotice(nick, "Game info will now be noticed to you.");
        } else {
            bot.sendMessage(nick, "Game info will now be messaged to you.");
        }
    }
    public void payPlayerDebt(String nick, int amount){
    	Player p = findJoined(nick);
    	if (amount <= 0){
    		infoPaymentTooLow(nick);
    	} else if (amount > p.getDebt()){
    		infoPaymentTooHigh(nick, p.getDebt());
    	} else if (amount > p.getCash()){
    		infoInsufficientFunds(nick);
    	} else if (amount == p.getCash()){
    		infoPaymentWillBankrupt(nick);
    	} else {
    		p.payDebt(amount);
            savePlayerData(p);
    		showPlayerDebtPayment(p, amount);
    	}
    }
    public void devoiceAll(){
    	Player p;
        for (int ctr=0; ctr<getNumberJoined(); ctr++){
            p = getJoined(ctr);
            savePlayerData(p);
            bot.deVoice(channel, findUser(p.getNick()));
        }
    }
    
    /* Player file management methods
     * Methods for reading and saving player data.
     */
    /**
     * Returns the total number of players who have played this game.
     * Counts the number of players who have played more than one round of this
     * game.
     * 
     * @return the total counted from players.txt
     */
    public int getTotalPlayers(){
    	try {
	    	ArrayList<String> nicks = new ArrayList<String>();
	        ArrayList<Integer> stacks = new ArrayList<Integer>();
	        ArrayList<Integer> bankrupts = new ArrayList<Integer>();
	        ArrayList<Integer> debts = new ArrayList<Integer>();
	        ArrayList<Integer> bjrounds = new ArrayList<Integer>();
	        ArrayList<Integer> tprounds = new ArrayList<Integer>();
	        ArrayList<Boolean> simples = new ArrayList<Boolean>();
	    	loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
	    	int total = 0, numLines = nicks.size();
            if (gameName.equals("Blackjack")){
                for (int ctr = 0; ctr < numLines; ctr++){
                    if (bjrounds.get(ctr) > 0){
                        total++;
                    }
                }
            } else if (gameName.equals("Texas Hold'em Poker")){
                for (int ctr = 0; ctr < numLines; ctr++){
                    if (tprounds.get(ctr) > 0){
                        total++;
                    }
                }
            }
        	return total;
    	} catch (IOException e){
		 	bot.log("Error reading players.txt!");
		 	return 0;
    	}
    }
    /**
     * Returns the specified statistic for a nick.
     * If a player is already joined or blacklisted, the value is read from the
     * Player object. Otherwise, a search is done in players.txt.
     * 
     * @param nick IRC user's nick
     * @param stat the statistic's name
     * @return the desired statistic
     */
    public int getPlayerStat(String nick, String stat){
        if (isJoined(nick) || isBlacklisted(nick)){
            Player p = findJoined(nick);
            if (p == null){
                p = findBlacklisted(nick);
            }
            if (stat.equals("cash")){
                return p.getCash();
            } else if (stat.equals("debt")){
                return p.getDebt();
            } else if (stat.equals("bankrupts")){
                return p.getBankrupts();
            } else if (stat.equals("bjrounds") || stat.equals("tprounds")){
                return p.getRounds();
            } else if (stat.equals("netcash")){
                return p.getNetCash();
            } else if (stat.equals("exists")){
                return 1;
            }
        }
        return loadPlayerStat(nick, stat);
    }
    /**
     * Searches players.txt for a specific player's statistic.
     * If the player or statistic is not found, Integer.MIN_VALUE is returned.
     * 
     * @param nick IRC user's nick
     * @param stat the statistic's name
     * @return the desired statistic or Integer.MIN_VALUE if not found
     */
    public int loadPlayerStat(String nick, String stat){
    	try {
        	ArrayList<String> nicks = new ArrayList<String>();
            ArrayList<Integer> stacks = new ArrayList<Integer>();
            ArrayList<Integer> bankrupts = new ArrayList<Integer>();
            ArrayList<Integer> debts = new ArrayList<Integer>();
            ArrayList<Integer> bjrounds = new ArrayList<Integer>();
            ArrayList<Integer> tprounds = new ArrayList<Integer>();
            ArrayList<Boolean> simples = new ArrayList<Boolean>();
        	loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
        	int numLines = nicks.size();
        	for (int ctr = 0; ctr < numLines; ctr++){
        		if (nick.toLowerCase().equals(nicks.get(ctr).toLowerCase())){
        			if (stat.equals("cash")){
        				return stacks.get(ctr);
        			} else if (stat.equals("debt")){
        				return debts.get(ctr);
        			} else if (stat.equals("bankrupts")){
        				return bankrupts.get(ctr);
        			} else if (stat.equals("bjrounds")){
        				return bjrounds.get(ctr);
        			} else if (stat.equals("tprounds")){
        				return tprounds.get(ctr);
        			} else if (stat.equals("netcash")){
        				return stacks.get(ctr)-debts.get(ctr);
        			} else if (stat.equals("exists")){
        				return 1;
        			}
                }
        	}
            return Integer.MIN_VALUE;
        } catch (IOException e){
        	bot.log("Error reading players.txt!");
        	return Integer.MIN_VALUE;
        }
    }
    /**
     * Loads a Player's data from players.txt.
     * If a matching nick is found then all statistics are loaded. Otherwise,
     * the player is loaded with default values.
     * 
     * @param p the Player to find
     */
    public void loadPlayerData(Player p) {
		try {
			boolean found = false;
			ArrayList<String> nicks = new ArrayList<String>();
			ArrayList<Integer> stacks = new ArrayList<Integer>();
			ArrayList<Integer> bankrupts = new ArrayList<Integer>();
			ArrayList<Integer> debts = new ArrayList<Integer>();
			ArrayList<Integer> bjrounds = new ArrayList<Integer>();
			ArrayList<Integer> tprounds = new ArrayList<Integer>();
			ArrayList<Boolean> simples = new ArrayList<Boolean>();
			loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
			int numLines = nicks.size();
			for (int ctr = 0; ctr < numLines; ctr++) {
				if (p.getNick().toLowerCase().equals(nicks.get(ctr).toLowerCase())) {
					if (stacks.get(ctr) <= 0) {
						p.setCash(getNewCash());
					} else {
						p.setCash(stacks.get(ctr));
					}
					p.setDebt(debts.get(ctr));
					p.setBankrupts(bankrupts.get(ctr));
                    if (gameName.equals("Blackjack")){
                        p.setRounds(bjrounds.get(ctr));
                    } else if (gameName.equals("Texas Hold'em Poker")){
                        p.setRounds(tprounds.get(ctr));
                    }
					p.setSimple(simples.get(ctr));
					found = true;
					break;
				}
			}
			if (!found) {
				p.setCash(getNewCash());
				p.setDebt(0);
				p.setBankrupts(0);
				p.setRounds(0);
				p.setSimple(true);
				infoNewPlayer(p.getNick());
			}
		} catch (IOException e) {
			bot.log("Error reading players.txt!");
		}
	}
	/**
     * Saves a Player's data into players.txt.
     * If a matching nick is found, the existing data is overwritten. Otherwise,
     * a new line is added for the Player.
     * 
     * @param p the Player to save
     */
    public void savePlayerData(Player p){
		boolean found = false;
		ArrayList<String> nicks = new ArrayList<String>();
		ArrayList<Integer> stacks = new ArrayList<Integer>();
		ArrayList<Integer> debts = new ArrayList<Integer>();
		ArrayList<Integer> bankrupts = new ArrayList<Integer>();
		ArrayList<Integer> bjrounds = new ArrayList<Integer>();
		ArrayList<Integer> tprounds = new ArrayList<Integer>();
		ArrayList<Boolean> simples = new ArrayList<Boolean>();
		int numLines;
		try {
			loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
			numLines = nicks.size();
			for (int ctr = 0; ctr < numLines; ctr++) {
				if (p.getNick().toLowerCase().equals(nicks.get(ctr).toLowerCase())) {
					stacks.set(ctr, p.getCash());
					debts.set(ctr, p.getDebt());
					bankrupts.set(ctr, p.getBankrupts());
                    if (gameName.equals("Blackjack")){
                        bjrounds.set(ctr, p.getRounds());
                    } else if (gameName.equals("Texas Hold'em Poker")){
                        tprounds.set(ctr, p.getRounds());
                    }
					simples.set(ctr, p.isSimple());
					found = true;
                    break;
				}
			}
			if (!found) {
				nicks.add(p.getNick());
				stacks.add(p.getCash());
				debts.add(p.getDebt());
				bankrupts.add(p.getBankrupts());
                if (gameName.equals("Blackjack")){
                    bjrounds.add(p.getRounds());
                    tprounds.add(0);
                } else if (gameName.equals("Texas Hold'em Poker")){
                    bjrounds.add(0);
                    tprounds.add(p.getRounds());
                }
				simples.add(p.isSimple());
			}
		} catch (IOException e) {
			bot.log("Error reading players.txt!");
		}

		try {
			savePlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
		} catch (IOException e) {
			bot.log("Error writing to players.txt!");
		}
	}
    public void checkPlayerFile(){
    	try {
    		BufferedReader out = new BufferedReader(new FileReader("players.txt"));
    		out.close();
    	} catch (IOException e){
    		bot.log("players.txt not found! Creating new players.txt...");
    		try {
	            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("players.txt")));
	            out.close();
    		} catch (IOException f){
        		bot.log("Error creating players.txt.");
        	}
        }
    }
    /**
     * Loads players.txt.
     * Reads the file's contents into a set of ArrayLists.
     * 
     * @param nicks
     * @param stacks
     * @param debts
     * @param bankrupts
     * @param bjrounds
     * @param tprounds
     * @param simples
     * @throws IOException
     */
    public static void loadPlayerFile(ArrayList<String> nicks, ArrayList<Integer> stacks,
    							ArrayList<Integer> debts, ArrayList<Integer> bankrupts, 
    							ArrayList<Integer> bjrounds, ArrayList<Integer> tprounds,
    							ArrayList<Boolean> simples) throws IOException {
    	BufferedReader in = new BufferedReader(new FileReader("players.txt"));
        StringTokenizer st;
        while (in.ready()){
            st = new StringTokenizer(in.readLine());
            nicks.add(st.nextToken());
            stacks.add(Integer.parseInt(st.nextToken()));
            debts.add(Integer.parseInt(st.nextToken()));
            bankrupts.add(Integer.parseInt(st.nextToken()));
            bjrounds.add(Integer.parseInt(st.nextToken()));
            tprounds.add(Integer.parseInt(st.nextToken()));
            simples.add(Boolean.parseBoolean(st.nextToken()));
        }
        in.close();
    }
    /**
     * Saves data to players.txt.
     * Saves a set of ArrayLists to players.txt.
     * 
     * @param nicks
     * @param stacks
     * @param debts
     * @param bankrupts
     * @param bjrounds
     * @param tprounds
     * @param simples
     * @throws IOException
     */
    public static void savePlayerFile(ArrayList<String> nicks, ArrayList<Integer> stacks,
								ArrayList<Integer> debts, ArrayList<Integer> bankrupts, 
								ArrayList<Integer> bjrounds, ArrayList<Integer> tprounds,
								ArrayList<Boolean> simples) throws IOException {
    	int numLines = nicks.size();
    	PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("players.txt")));
        for (int ctr = 0; ctr<numLines; ctr++){
            out.println(nicks.get(ctr)+" "+stacks.get(ctr)+" "+debts.get(ctr)+
            			" "+bankrupts.get(ctr)+" "+bjrounds.get(ctr)+" "+
            			tprounds.get(ctr)+" "+simples.get(ctr));
        }
        out.close();
    }
    
    /* Generic card management methods */
	public void burnCard(){
		deck.addToDiscard(deck.takeCard());
        if (deck.getNumberCards() == 0) {
			showDeckEmpty();
			deck.refillDeck();
		}
	}
    public void dealCard(Hand h) {
		h.add(deck.takeCard());
		if (deck.getNumberCards() == 0) {
			showDeckEmpty();
			deck.refillDeck();
		}
	}
    
    /* 
     * Channel output methods to reduce clutter.
     * These methods will all send a specific message or set of
     * messages to the main channel.
     */
    abstract public void showGameStats();
	public void showReloadSettings() {
		bot.sendMessage(channel, iniFile+" has been reloaded.");
	}
    /**
     * Outputs all of a player's data relevant to this game.
     * Sends a message to a player the list of statistics separated by '|'
     * character.
     * 
     * @param nick the player's nick
     */
    public void showPlayerAllStats(String nick){
        int cash = getPlayerStat(nick, "cash");
        int debt = getPlayerStat(nick, "debt");
        int net = getPlayerStat(nick, "netcash");
        int bankrupts = getPlayerStat(nick, "bankrupts");
        int rounds = 0;
        if (gameName.equals("Blackjack")){
            rounds = getPlayerStat(nick, "bjrounds");
        } else if (gameName.equals("Texas Hold'em Poker")){
            rounds = getPlayerStat(nick, "tprounds");
        }
        if (cash != Integer.MIN_VALUE) {
            bot.sendMessage(channel, nick+" | Cash: $"+formatNumber(cash)+" | Debt: $"+
                    formatNumber(debt)+" | Net: $"+formatNumber(net)+" | Bankrupts: "+
                    formatNumber(bankrupts)+" | Rounds: "+formatNumber(rounds));
        } else {
            bot.sendMessage(channel, "No data found for " + nick + ".");
        }
    }
	 /**
     * Outputs the top N players for a given statistic.
     * Sends a message to channel with the list of players sorted by ranking.
     * 
     * @param stat the statistic used for ranking
     * @param n the length of the list up to n players
     */
    public void showTopPlayers(String stat, int n) {
        if (n < 1){
            throw new IllegalArgumentException();
        }
		int highIndex;
		try {
			ArrayList<String> nicks = new ArrayList<String>();
			ArrayList<Integer> stacks = new ArrayList<Integer>();
			ArrayList<Integer> bankrupts = new ArrayList<Integer>();
			ArrayList<Integer> debts = new ArrayList<Integer>();
			ArrayList<Integer> bjrounds = new ArrayList<Integer>();
			ArrayList<Integer> tprounds = new ArrayList<Integer>();
			ArrayList<Boolean> simples = new ArrayList<Boolean>();
			loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
			ArrayList<Integer> test = new ArrayList<Integer>();
            int length = Math.min(n, nicks.size());
			String title = Colors.BLACK + ",08Top " + length;
			String list;
			if (stat.equals("cash")) {
				test = stacks;
				title += " Cash:";
			} else if (stat.equals("debt")) {
				test = debts;
				title += " Debt:";
			} else if (stat.equals("bankrupts")) {
				test = bankrupts;
				title += " Bankrupts:";
			} else if (stat.equals("net") || stat.equals("netcash")) {
				for (int ctr = 0; ctr < nicks.size(); ctr++) {
					test.add(stacks.get(ctr) - debts.get(ctr));
				}
				title += " Net Cash:";
			} else if (stat.equals("rounds")) {
                if (gameName.equals("Blackjack")){
                    test = bjrounds;
                    title += " Blackjack Rounds:";
                } else if (gameName.equals("Texas Hold'em Poker")){
                    test = tprounds;
                    title += " Texas Hold'em Poker Rounds:";
                }
			} else {
				throw new IllegalArgumentException();
			}
			list = title;
            // Find the player with the highest value, add to output string and remove.
            // Repeat n times or for the length of the list.
			for (int ctr = 1; ctr <= length; ctr++){
				highIndex = 0;
				for (int ctr2 = 0; ctr2 < nicks.size(); ctr2++) {
					if (test.get(ctr2) > test.get(highIndex)) {
						highIndex = ctr2;
					}
				}
				if (stat.equals("rounds") || stat.equals("bankrupts")) {
					list += " #" + ctr + ": " + Colors.WHITE + ",04 "
							+ nicks.get(highIndex) + " " 
							+ formatNumber(test.get(highIndex)) + " "
							+ Colors.BLACK + ",08";
				} else {
					list += " #" + ctr + ": " + Colors.WHITE + ",04 "
							+ nicks.get(highIndex) + " $"
							+ formatNumber(test.get(highIndex)) + " "
							+ Colors.BLACK + ",08";
				}
				nicks.remove(highIndex);
				test.remove(highIndex);
				if (nicks.isEmpty() || ctr == length) {
					break;
				}
                // Output and reset after 10 players
                if (ctr % 10 == 0){
                    bot.sendMessage(channel, list);
                    list = title;
                }
			}
			bot.sendMessage(channel, list);
		} catch (IOException e) {
			bot.log("Error reading players.txt!");
		}
	}
    public void showSetting(String param, String value){
		bot.sendMessage(channel, param+"="+value);
	}
	public void showUpdateSetting(String param) {
		bot.sendMessage(channel, param + " setting has been updated.");
	}
    public void showTurn(Player p) {
    	bot.sendMessage(channel,"It's now "+p.getNickStr()+"'s turn.");
    }
    public void showJoin(Player p){
        bot.sendMessage(channel, p.getNickStr()+" has joined the game. Players: "+Colors.BOLD+getNumberJoined());
    }
    public void showLeave(Player p){
    	if (p.isBankrupt()){
    		bot.sendMessage(channel, p.getNickStr()+" has gone bankrupt and left the game. Players: "+Colors.BOLD+getNumberJoined());
    	} else {
    		bot.sendMessage(channel, p.getNickStr()+" has left the game. Players: "+Colors.BOLD+getNumberJoined());
    	}
    }
    public void showNoPlayers(){
        bot.sendMessage(channel, "Not enough players.");
    }
    public void showGameStart(){
        bot.sendMessage(channel, getGameNameStr()+" has started.");
    }
    public void showGameEnd(){
        bot.sendMessage(channel, getGameNameStr()+" has ended.");
    }
    public void showGameName(){
        bot.sendMessage(channel, "Currently running "+getGameNameStr()+".");
    }
    public void showStartRound(){
        bot.sendMessage(channel, "Starting another round of "+getGameNameStr()+" in 5 seconds...");
    }
    public void showEndRound(){
        bot.sendMessage(channel, "End of "+getGameNameStr()+" round. Type .go for a new round.");
    }
    public void showNumDecks(){
		bot.sendMessage(channel, "This game of "+getGameNameStr()+" is using "+deck.getNumberDecks()+" deck(s) of cards.");
    }
    public void showNumCards(){
        bot.sendMessage(channel, deck.getNumberCards()+" cards left in the deck.");
    }
    public void showNumDiscards(){
        bot.sendMessage(channel, deck.getNumberDiscards()+" cards in the discard pile.");
    }
    public void showDeckEmpty() {
		bot.sendMessage(channel, "The deck is empty. Refilling with discards...");
	}
    public void showPlayers(){
        bot.sendMessage(channel, "Joined: "+getPlayerListString(joined));
    }
    public void showWaitlist(){
        bot.sendMessage(channel, "Waiting: "+getPlayerListString(waitlist));
    }
    public void showBlacklist(){
        bot.sendMessage(channel, "Bankrupt: "+getPlayerListString(blacklist));
    }
    public void showPlayerCash(String nick){
    	int cash = getPlayerStat(nick, "cash");
    	if (cash != Integer.MIN_VALUE){
        	bot.sendMessage(channel, nick+" has $"+formatNumber(cash)+".");
        } else {
        	bot.sendMessage(channel, "No data found for "+nick+".");
        }
    }
    public void showPlayerNetCash(String nick){
    	int netcash = getPlayerStat(nick, "netcash");
    	if (netcash != Integer.MIN_VALUE){
        	bot.sendMessage(channel, nick+" has $"+formatNumber(netcash)+" in net cash.");
        } else {
        	bot.sendMessage(channel, "No data found for "+nick+".");
        }
    }
    public void showPlayerDebt(String nick){
    	int debt = getPlayerStat(nick, "debt");
    	if (debt != Integer.MIN_VALUE){
        	bot.sendMessage(channel, nick+" has $"+formatNumber(debt)+" in debt.");
        } else {
        	bot.sendMessage(channel, "No data found for "+nick+".");
        }
    }
    public void showPlayerBankrupts(String nick){
    	int bankrupts = getPlayerStat(nick, "bankrupts");
    	if (bankrupts != Integer.MIN_VALUE){
        	bot.sendMessage(channel, nick+" has gone bankrupt "+bankrupts+" time(s).");
        } else {
        	bot.sendMessage(channel, "No data found for "+nick+".");
        }
    }
    public void showPlayerRounds(String nick){
        int rounds = 0;
        if (gameName.equals("Blackjack")){
            rounds = getPlayerStat(nick, "bjrounds");
        } else if (gameName.equals("Texas Hold'em Poker")){
            rounds = getPlayerStat(nick, "tprounds");
        }
		
		if (rounds != Integer.MIN_VALUE) {
			bot.sendMessage(channel, nick + " has played " + rounds + " round(s) of " + getGameNameStr() + ".");
		} else {
			bot.sendMessage(channel, "No data found for " + nick + ".");
		}
	} 
    public void showPlayerDebtPayment(Player p, int amount){
    	bot.sendMessage(channel, p.getNickStr()+" has made a debt payment of $"+formatNumber(amount)+". "+p.getNickStr()+"'s debt is now $"+formatNumber(p.getDebt())+".");
    }
    public void showSeparator() {
		bot.sendMessage(channel, Colors.BOLD+ "------------------------------------------------------------------");
	}
    
    /* 
     * Private messages to players.
     * These methods will all send notices/messages to the specified nick.
     */
	public void infoGameRules(String nick) {
		bot.sendNotice(nick,getGameRulesStr());
	}
	public void infoGameHelp(String nick) {
		bot.sendNotice(nick,getGameHelpStr());
	}
    public void infoGameCommands(String nick){
    	bot.sendNotice(nick,getGameNameStr()+" commands:");
    	bot.sendNotice(nick,getGameCommandStr());
    }
    public void infoNewPlayer(String nick){
    	bot.sendNotice(nick, "Welcome to "+getGameNameStr()+"! Here's $"+formatNumber(getNewCash())+" to get you started!");
    }
    public void infoNewNick(String nick){
    	bot.sendNotice(nick, "Welcome to "+getGameNameStr()+"! For help, type .ghelp!");
    }
    public void infoMaxPlayers(String nick){
        bot.sendNotice(nick, "Unable to join. The maximum number of players has been reached.");
    }
    public void infoAlreadyJoined(String nick){
    	bot.sendNotice(nick, "You have already joined!");
    }
    public void infoNotJoined(String nick){
    	bot.sendNotice(nick, "You are not currently joined!");
    }
    public void infoJoinWaitlist(String nick){
    	bot.sendNotice(nick, "You have joined the waitlist and will be automatically added next round.");
	}
    public void infoLeaveWaitlist(String nick){
    	bot.sendNotice(nick, "You have left the waitlist and will not be automatically added next round.");
    }
    public void infoAlreadyWaitlisted(String nick){
    	bot.sendNotice(nick, "You have already joined the waitlist!");
    }
    public void infoBlacklisted(String nick){
    	bot.sendNotice(nick, "You have gone bankrupt. Please wait for a loan to join again.");
    }
    public void infoWaitRoundEnd(String nick){
    	bot.sendNotice(nick, "A round is in progress! Wait for the round to end.");
    }
    public void infoRoundStarted(String nick){
    	bot.sendNotice(nick, "A round is in progress!");
    }
    public void infoNotStarted(String nick){
    	bot.sendNotice(nick, "No round in progress!");
    }
    public void infoNotBetting(String nick){
    	bot.sendNotice(nick, "No betting in progress!");
    }
    public void infoNotTurn(String nick){
    	bot.sendNotice(nick, "It's not your turn!");
    }
    public void infoNoCards(String nick){
    	bot.sendNotice(nick, "No cards have been dealt yet!");
    }
    public void infoOpsOnly(String nick){
    	bot.sendNotice(nick, "This command may only be used by channel Ops.");
    }
    public void infoNickChange(String nick){
    	bot.sendNotice(nick, "You have changed nicks while joined. Your old nick will be removed " +
    			"and your new nick will be joined, if possible.");
    }
    public void infoPaymentTooLow(String nick){
    	bot.sendNotice(nick, "Minimum payment is $1. Try again.");
    }
    public void infoPaymentTooHigh(String nick, int max){
    	bot.sendNotice(nick, "Your outstanding debt is only $"+formatNumber(max)+". Try again.");
    }
    public void infoPaymentWillBankrupt(String nick){
    	bot.sendNotice(nick, "You cannot go bankrupt trying to pay off your debt. Try again.");
    }
    public void infoInsufficientFunds(String nick){
    	bot.sendNotice(nick, "Insufficient funds. Try again.");
    }
    public void infoNickNotFound(String nick, String searchNick){
    	bot.sendNotice(nick, searchNick + " was not found.");
    }
    public void infoNoParameter(String nick){
    	bot.sendNotice(nick, "Parameter(s) missing!");
    }
    public void infoBadParameter(String nick){
        bot.sendNotice(nick,"Bad parameter(s). Try again.");
    }
    public void infoBetTooLow(String nick){
        bot.sendNotice(nick, "Minimum bet is $1. Try again.");
    }
    public void infoBetTooHigh(String nick, int max){
        bot.sendNotice(nick, "Maximum bet is $"+formatNumber(max)+". Try again.");
    }
	public void infoPlayerBankrupt(String nick) {
		bot.sendNotice(nick, "You've lost all your money. Please wait " + respawnTime/60 + " minute(s) for a loan.");
	}
    public void infoGameCommandHelp(String nick, String command){
        bot.sendNotice(nick, getCommandHelp(command));
    }
    
    /* Reveals cards from the card deck */
    public void infoDeckCards(String nick, char type, int num){
        if (num < 1){
            throw new IllegalArgumentException();
        }
    	int cardIndex=0, numOut, n;
    	String cardStr;
    	ArrayList<Card> tCards;
    	if (type == 'c'){
    		tCards = deck.getCards();
    	} else {
    		tCards = deck.getDiscards();
    	}
    	n = Math.min(num, tCards.size());
        while(n > 0){
        	cardStr = cardIndex+"";
        	numOut = Math.min(n, 25);
        	cardStr += "-"+(cardIndex+numOut-1)+": ";
    		n -= numOut;
        	for (int ctr=cardIndex; ctr < cardIndex+numOut; ctr++){
    			cardStr += tCards.get(ctr)+" ";
    		}
        	cardIndex+=numOut;
        	bot.sendNotice(nick, cardStr.substring(0, cardStr.length()-1)+Colors.NORMAL);
        }
    }
    
    /* Formatted strings */
    public String getGameNameStr(){
    	return Colors.BOLD + gameName + Colors.BOLD;
    }
	public String getGameHelpStr() {
		return "For a list of game commands, type .gcommands. For house rules, type .grules. "+
                "For help on individual commands, type .ghelp <command>.";
	}
    abstract public String getGameRulesStr();
    abstract public String getGameCommandStr();
    public static String formatNumber(int n){
    	return String.format("%,d", n);
    }
    protected static String getPlayerListString(ArrayList<? extends Player> playerList){
        String outStr;
        int size = playerList.size();
        if (size == 0){
            outStr = Colors.BOLD+"0"+Colors.BOLD+" players!";
        } else if (size == 1){
            outStr = Colors.BOLD+"1"+Colors.BOLD+" player: "+playerList.get(0).getNick();
        } else {
            outStr = Colors.BOLD+size+Colors.BOLD+" players: ";
            for (int ctr=0; ctr < size; ctr++){
                if (ctr == size-1){
                    outStr += playerList.get(ctr).getNick();
                } else {
                    outStr += playerList.get(ctr).getNick()+", ";
                }
            }
        }
        return outStr;
    }

    /**
     * Searches the help file for data on the specified command.
     * @param command
     * @return the help data for the command
     */
    protected String getCommandHelp(String command){
        try {		
            BufferedReader in = new BufferedReader(new FileReader(helpFile));
            StringTokenizer st;
            String c,d="";
            boolean found = false;
            while (in.ready()){
                st = new StringTokenizer(in.readLine(), "=");
                if (st.countTokens() == 2){
                    c = st.nextToken();
                    d = st.nextToken();
                    if (c.equals(command)){
                        found = true;
                        break;
                    }
                }
            }
            in.close();
            if (found){
                return d;
            } else {
                return "Help for \'"+command+"\' not found!";
            }
        } catch (IOException e) {
			bot.log("Error reading from help file!");
            return "Error reading from help file!";
		}
    }
}