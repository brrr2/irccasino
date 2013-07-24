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
	public class StartRoundTask extends TimerTask{
		CardGame game;
		public StartRoundTask(CardGame g){
			game = g;
		}
		@Override
		public void run(){
			game.startRound();
		}
	}
    /* Idle task for card games */
	public class IdleOutTask extends TimerTask {
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
	public class RespawnTask extends TimerTask {
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
    protected Channel channel; //channel where game is being played
    protected String gameName;
    protected ArrayList<Player> joined, blacklist, waitlist;
    protected CardDeck deck;
    protected Player currentPlayer;
    protected boolean inProgress, betting;
    protected Timer idleOutTimer, startRoundTimer, respawnTimer;
    protected IdleOutTask idleOutTask;
    protected int idleOutTime, respawnTime, newcash;
    private StartRoundTask startRoundTask;
    private ArrayList<RespawnTask> respawnTasks;
    
    /**
     * Class constructor for generic CardGame
     * 
     * @param parent	the bot that creates an instance of this class
     * @param gameChannel	the IRC channel in which the game is to be run.
     */
    public CardGame (PircBotX parent, Channel gameChannel){
        bot = parent;
        channel = gameChannel;
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
    
    abstract public void processMessage(User user, String msg, String origMsg);
    public void processJoin(User user){
        String nick = user.getNick();
    	if (loadPlayerStat(nick, "exists") != 1){
    		infoNewNick(nick);
    	}
    }
    
    public void processQuit(User user){
        String nick = user.getNick();
		if (isJoined(nick) || isWaitlisted(nick)){
			leave(nick);
		}
    }
    
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
    
    /* 
     * Game management methods
     * These methods control various aspects of the game. 
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
		if (isJoined(nick)) {
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
    	Player p;
    	while(!waitlist.isEmpty()){
    		p = getWaitlisted(0);
    		addPlayer(p);
    		removeWaitlisted(p);
    	}
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
		if (!respawnTasks.isEmpty()) {
			for (int ctr = 0; ctr < respawnTasks.size(); ctr++) {
				respawnTasks.get(ctr).cancel();
			}
            respawnTasks.clear();
            respawnTimer.purge();
		}
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
    
    /* 
     * Player management methods 
     * These methods are intended to manage players. They include
     * stats management and play management.
     */
    abstract public int getTotalPlayers();
    abstract public void loadPlayerData(Player p);
    abstract public void savePlayerData(Player p);
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
        	user = (User) it.next();
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
    public void loadPlayerFile(ArrayList<String> nicks, ArrayList<Integer> stacks,
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
    public void savePlayerFile(ArrayList<String> nicks, ArrayList<Integer> stacks,
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
    
    /* 
     * Generic card dealing methods
     */
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
    abstract public void showTopPlayers(String param, int n);
    abstract public void showPlayerRounds(String nick);
    abstract public void showPlayerAllStats(String nick);
    abstract public void showReloadSettings();
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
    	if (p.getCash() == 0){
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
    public void showPlayerDebtPayment(Player p, int amount){
    	bot.sendMessage(channel, p.getNickStr()+" has made a debt payment of $"+formatNumber(amount)+". "+p.getNickStr()+"'s debt is now $"+formatNumber(p.getDebt())+".");
    }
    public void showSeparator() {
		bot.sendMessage(channel, Colors.BOLD+ "------------------------------------------------------------------");
	}
    
    /* 
     * Player/nick output methods to reduce clutter elsewhere.
     * These methods will all send notices to the intended
     * recipient with a specific message.
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
		Player p = findJoined(nick);
		if (p.isSimple()) {
			bot.sendNotice(nick, "You've lost all your money. Please wait " 
						+ respawnTime/60 + " minute(s) for a loan.");
		} else {
			bot.sendMessage(nick, "You've lost all your money. Please wait "
						+ respawnTime/60 + " minute(s) for a loan.");
		}
	}
    
    /* Reveals cards in the deck/discards */
    public void infoDeckCards(String nick, char type, int num){
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
    
    /* Parameter handling */   
    public static int parseNumberParam(String str){
        StringTokenizer st = new StringTokenizer(str);
        String a;
        a = st.nextToken();
        a = st.nextToken();
        return Integer.parseInt(a);
    }
    public static String parseStringParam(String str){
        StringTokenizer st = new StringTokenizer(str);
        String a;
        a = st.nextToken();
        a = st.nextToken();
        return a;
    }
    public static String[] parseIniParams(String str){
    	StringTokenizer st = new StringTokenizer(str);
        String a,b;
        a = st.nextToken();
        a = st.nextToken();
        b = st.nextToken();
        String[] out = {a,b};
        return out;
    }
    
    /* Formatted strings */
    public String getGameNameStr(){
    	return Colors.BOLD + gameName + Colors.BOLD;
    }
	public String getGameHelpStr() {
		return "For help on how to play "
				+ getGameNameStr()
				+ ", please visit an online resource. "
				+ "For game commands, type .gcommands. For house rules, type .grules.";
	}
    abstract public String getGameRulesStr();
    abstract public String getGameCommandStr();
    public static String getWinStr(){
    	return Colors.GREEN+",01"+" WIN "+Colors.NORMAL;
    }
    public static String getLossStr(){
    	return Colors.RED+",01"+" LOSS "+Colors.NORMAL;
    }
    public static String formatNumber(int n){
    	return String.format("%,d", n);
    }
    public static String getPlayerListString(ArrayList<? extends Player> playerList){
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
}