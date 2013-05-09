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

import java.io.*;
import java.util.*;

import org.pircbotx.*;
import org.pircbotx.hooks.*;
import org.pircbotx.hooks.events.*;


public abstract class CardGame extends ListenerAdapter<PircBotX>{
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
	public static class RespawnTask extends TimerTask {
		Player p;
		CardGame game;

		public RespawnTask(Player p, CardGame g) {
			this.p = p;
			game = g;
		}

		@Override
		public void run() {
			ArrayList<Timer> timers = game.getRespawnTimers();
			p.setCash(game.getNewCash());
			p.addDebt(game.getNewCash());
			game.bot.sendMessage(game.channel, p.getNickStr() + " has been loaned $"
							+ String.format("%,d", game.getNewCash()) + ". Please bet responsibly.");
			game.blacklist.remove(p);
			game.savePlayerData(p);
			timers.remove(this);
		}
	}

    protected PircBotX bot; //bot handling the game
    protected Channel channel; //channel where game is being played
    protected String gameName;
    protected ArrayList<Player> players, blacklist, waitlist;
    protected CardDeck deck;
    protected Player currentPlayer;
    protected boolean inProgress, betting;
    protected Timer idleOutTimer, startRoundTimer;
    protected int idleOutTime, respawnTime, newcash;
    private ArrayList<Timer> respawnTimers;
    
    /**
     * Class constructor for generic CardGame
     * 
     * @param parent	the bot that creates an instance of this ListenerAdapter
     * @param gameChannel	the IRC channel in which the game is to be run.
     */
    public CardGame (PircBotX parent,Channel gameChannel){
        bot = parent;
        channel = gameChannel;
        players = new ArrayList<Player>();
        blacklist = new ArrayList<Player>();
        waitlist = new ArrayList<Player>();
        respawnTimers = new ArrayList<Timer>();
        inProgress = false;
        betting = false;
        checkPlayerFile();
    }
    
    @Override
    public void onJoin(JoinEvent e){
    	User user = e.getUser();
    	if (loadPlayerStat(user.getNick(), "exists") != 1){
    		infoNewUser(user);
    	}
    }
    
    /* Accessor methods */
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
    
    /* Game management methods */
    abstract public void startRound();
    abstract public void endRound();
    abstract public void endGame();
    abstract public void resetGame();
    abstract public void leaveGame(User u);
    abstract public void resetPlayers();
    abstract public void setIdleOutTimer();
    abstract public void cancelIdleOutTimer();
    abstract public void setSetting(String[] params);
    abstract public String getSetting(String param);
    abstract public void loadSettings();
    abstract public void saveSettings();
    public void mergeWaitlist(){
    	Player p;
    	while(!waitlist.isEmpty()){
    		p = waitlist.get(0);
    		players.add(0,p);
    		waitlist.remove(p);
    		showPlayerJoin(p);
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
    public void setRespawnTimer(Player p) {
		Timer t = new Timer();
		t.schedule(new RespawnTask(p, this), respawnTime*1000);
		respawnTimers.add(t);
	}
	public void cancelRespawnTimers() {
		if (respawnTimers.size() != 0) {
			for (int ctr = 0; ctr < respawnTimers.size(); ctr++) {
				respawnTimers.get(0).cancel();
				respawnTimers.remove(0);
			}
		}
	}
	public ArrayList<Timer> getRespawnTimers() {
		return respawnTimers;
	}

    /* Card management methods */
    abstract public void discardPlayerHand(Player p);
    public void setStartRoundTimer(){
    	startRoundTimer = new Timer();
    	startRoundTimer.schedule(new StartRoundTask(this), 5000);
    }
    public void cancelStartRoundTimer(){
    	if (startRoundTimer != null){
	    	startRoundTimer.cancel();
	    	startRoundTimer = null;
    	}
    }
    
    /* Player management methods */
    abstract public int getPlayerRounds(String nick);
    abstract public void loadPlayerData(Player p);
    abstract public void savePlayerData(Player p);
    abstract public void addPlayer(User user);
    abstract public void addWaitingPlayer(User user);
    public boolean playerJoined(User user){
        Player p = findPlayer(user);
        if (p != null){
            return true;
        }
        return false;
    }
    public boolean playerJoined(String nick){
    	for (int ctr=0; ctr<players.size(); ctr++){
            if (players.get(ctr).getNick().toLowerCase().equals(nick.toLowerCase())){
                return true;
            }  
        }
        return false;
    }
    public boolean playerWaiting(User user){
    	Player p = findWaiting(user);
        if (p != null){
            return true;
        }
        return false;
    }
    public void removePlayer(User user){
        Player p = findPlayer(user);
        discardPlayerHand(p);
        players.remove(p);
        savePlayerData(p);
        showPlayerLeave(p);
    }
    public void removeWaiting(User user){
    	Player p = findWaiting(user);
    	waitlist.remove(p);
        infoPlayerLeaveWaiting(p);
    }
    public Player getNextPlayer(){
        int index = players.indexOf(currentPlayer);
        if (index + 1 < getNumberPlayers()){
            return players.get(index+1);
        } else {
            return null;
        }
    }
    public Player getPlayer(int num){
        return players.get(num);
    }
    public Player getWaiting(int num){
    	return waitlist.get(num);
    }
    public Player getBankrupt(int num){
    	return blacklist.get(num);
    }
    public Player findPlayer(User u){
        for (int ctr=0; ctr<players.size(); ctr++){
            if (players.get(ctr).getNick().equals(u.getNick())){
                return players.get(ctr);
            }  
        }
        return null;
    }
    public Player findPlayer(String nick){
        for (int ctr=0; ctr<getNumberPlayers(); ctr++){
            if (players.get(ctr).getNick().toLowerCase().equals(nick.toLowerCase())){
                return players.get(ctr);
            }  
        }
        return null;
    }
    public Player findWaiting(User user){
    	for (int ctr=0; ctr< getNumberWaiting(); ctr++){
            if (waitlist.get(ctr).getNick().equals(user.getNick())){
                return waitlist.get(ctr);
            }  
        }
        return null;
    }
    public int getNumberPlayers(){
        return players.size();
    }
    public int getNumberWaiting(){
    	return waitlist.size();
    }
    public int getNumberBankrupt(){
    	return blacklist.size();
    }
    public Player getCurrentPlayer(){
        return currentPlayer;
    }
    public void togglePlayerSimple(User u){
        Player p = findPlayer(u);
        p.setSimple(!p.isSimple());
        if (p.isSimple()){
            bot.sendNotice(p.getUser(), "Game info will now be noticed to you.");
        } else {
            bot.sendMessage(p.getUser(), "Game info will now be messaged to you.");
        }
    }
    public int getPlayerCash(String nick){
    	if (playerJoined(nick)){
    		return findPlayer(nick).getCash();
    	} else {
    		return loadPlayerStat(nick, "cash");
    	}
    }
    public int getPlayerDebt(String nick){
    	if (playerJoined(nick)){
    		return findPlayer(nick).getDebt();
    	} else {
    		return loadPlayerStat(nick, "debt");
    	}
    }
    public int getPlayerBankrupts(String nick){
    	if (playerJoined(nick)){
    		return findPlayer(nick).getBankrupts();
    	} else {
    		return loadPlayerStat(nick, "bankrupts");
    	}
    }
    public int getPlayerNetCash(String nick){
    	if (playerJoined(nick)){
    		return findPlayer(nick).getCash()-findPlayer(nick).getDebt();
    	} else {
    		return loadPlayerStat(nick, "netcash");
    	}
    }
    public int loadPlayerStat(String nick, String stat){
    	try {
        	ArrayList<String> nicks = new ArrayList<String>();
            ArrayList<Boolean> simples = new ArrayList<Boolean>();
            ArrayList<Integer> stacks = new ArrayList<Integer>();
            ArrayList<Integer> bankrupts = new ArrayList<Integer>();
            ArrayList<Integer> debts = new ArrayList<Integer>();
            ArrayList<Integer> bjrounds = new ArrayList<Integer>();
        	loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, simples);
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
        			} else if (stat.equals("netcash")){
        				return stacks.get(ctr)-debts.get(ctr);
        			} else if (stat.equals("exists")){
        				return 1;
        			}
                }
        	}
            return Integer.MIN_VALUE;
        } catch (IOException e){
        	System.out.println("Error reading players.txt!");
        	return Integer.MIN_VALUE;
        }
    }
    public void loadPlayerFile(ArrayList<String> nicks, ArrayList<Integer> stacks,
    							ArrayList<Integer> debts, ArrayList<Integer> bankrupts, 
    							ArrayList<Integer> bjrounds, ArrayList<Boolean> simples) throws IOException {
    	BufferedReader f = new BufferedReader(new FileReader("players.txt"));
        StringTokenizer st;
        while (f.ready()){
            st = new StringTokenizer(f.readLine());
            nicks.add(st.nextToken());
            stacks.add(Integer.parseInt(st.nextToken()));
            debts.add(Integer.parseInt(st.nextToken()));
            bankrupts.add(Integer.parseInt(st.nextToken()));
            bjrounds.add(Integer.parseInt(st.nextToken()));
            simples.add(Boolean.parseBoolean(st.nextToken()));
        }
        f.close();
    }
    public void savePlayerFile(ArrayList<String> nicks, ArrayList<Integer> stacks,
			ArrayList<Integer> debts, ArrayList<Integer> bankrupts, ArrayList<Integer> bjrounds, 
			ArrayList<Boolean> simples) throws IOException {
    	int numLines = nicks.size();
    	PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("players.txt")));
        for (int ctr = 0; ctr<numLines; ctr++){
            out.println(nicks.get(ctr)+" "+stacks.get(ctr)+" "+debts.get(ctr)+
            			" "+bankrupts.get(ctr)+" "+bjrounds.get(ctr)+" "+simples.get(ctr));
        }
        out.close();
    }
    public void saveAllPlayers(){
    	Player p;
        for (int ctr=0; ctr<getNumberPlayers(); ctr++){
            p = getPlayer(ctr);
            savePlayerData(p);
        }
        for (int ctr=0; ctr<getNumberWaiting(); ctr++){
            p = getWaiting(ctr);
            savePlayerData(p);
        }
        for (int ctr=0; ctr<getNumberBankrupt(); ctr++){
            p = getBankrupt(ctr);
            savePlayerData(p);
        }
    }
    public void checkPlayerFile(){
    	try {
    		BufferedReader f = new BufferedReader(new FileReader("players.txt"));
    		f.close();
    	} catch (IOException e){
    		System.out.println("players.txt not found! Creating new players.txt...");
    		try {
	            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("players.txt")));
	            out.close();
    		} catch (IOException f){
        		System.out.println("Error creating players.txt.");
        	}
        }
    }
    public boolean isPlayerIdledOut(Player p){
    	return p.getIdledOut();
    }
    public boolean isPlayerBankrupt(Player p){
        return (p.getCash() == 0);
    }
    public boolean isBlacklisted(User user){
        for (int ctr=0; ctr<blacklist.size(); ctr++){
            if (blacklist.get(ctr).getNick().equals(user.getNick())){
                return true;
            }
        }
        return false;
    }
    public void payPlayerDebt(User user, int amount){
    	Player p = findPlayer(user);
    	if (amount <= 0){
    		infoPaymentTooLow(p);
    	} else if (amount > p.getDebt()){
    		infoPaymentTooHigh(p);
    	} else if (amount > p.getCash()){
    		infoInsufficientFunds(p);
    	} else if (amount == p.getCash()){
    		infoPaymentWillBankrupt(p);
    	} else {
    		p.payDebt(amount);
    		showPlayerDebtPayment(p, amount);
    	}
    }
    
    /* Channel output methods to reduce clutter */
    abstract public void showTopPlayers(String param, int n);
    abstract public void showPlayerRounds(String nick);
    public void showSetting(String param, String value){
		bot.sendMessage(channel, param+"="+value);
	}
	public void showUpdateSetting(String param) {
		bot.sendMessage(channel, param + " setting has been updated.");
	}
    public void showPlayerTurn(Player p) {
    	bot.sendMessage(channel,"It's now "+p.getNickStr()+"'s turn.");
    }
    public void showPlayerJoin(Player p){
        bot.sendMessage(channel, p.getNickStr()+" has joined the game.");
    }
    public void showPlayerLeave(Player p){
        bot.sendMessage(channel, p.getNickStr()+" has left the game.");
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
    public void showPlayers(){
        String outStr;
        if (getNumberPlayers()==0){
            outStr = "0 players joined!";
        } else {
            outStr = getNumberPlayers()+ " player(s): ";
            for (int ctr=0; ctr<getNumberPlayers(); ctr++){
                outStr += getPlayer(ctr).getNick()+" "; 
            }
        }
        bot.sendMessage(channel, outStr);
    }
    public void showWaiting(){
    	String outStr;
        if (getNumberWaiting()==0){
            outStr = "0 players waiting!";
        } else {
            outStr = getNumberWaiting()+ " player(s) waiting: ";
            for (int ctr=0; ctr < getNumberWaiting(); ctr++){
                outStr += getWaiting(ctr).getNick()+" "; 
            }
        }
        bot.sendMessage(channel, outStr);
    }
    public void showBankrupt(){
    	String outStr;
        if (getNumberBankrupt()==0){
            outStr = "0 players bankrupt!";
        } else {
            outStr = getNumberBankrupt()+ " player(s) bankrupt: ";
            for (int ctr=0; ctr < getNumberBankrupt(); ctr++){
                outStr += getBankrupt(ctr).getNick()+" "; 
            }
        }
        bot.sendMessage(channel, outStr);
    }
    public void showPlayerCash(String nick){
    	int cash = getPlayerCash(nick);
    	if (cash != Integer.MIN_VALUE){
        	bot.sendMessage(channel, nick+" has $"+formatNumber(cash)+".");
        } else {
        	bot.sendMessage(channel, "No data found for "+nick+".");
        }
    }
    public void showPlayerNetCash(String nick){
    	int netcash = getPlayerNetCash(nick);
    	if (netcash != Integer.MIN_VALUE){
        	bot.sendMessage(channel, nick+" has $"+formatNumber(netcash)+" in net cash.");
        } else {
        	bot.sendMessage(channel, "No data found for "+nick+".");
        }
    }
    public void showPlayerDebt(String nick){
    	int debt = getPlayerDebt(nick);
    	if (debt != Integer.MIN_VALUE){
        	bot.sendMessage(channel, nick+" has $"+formatNumber(debt)+" in debt.");
        } else {
        	bot.sendMessage(channel, "No data found for "+nick+".");
        }
    }
    public void showPlayerBankrupts(String nick){
    	int bankrupts = getPlayerBankrupts(nick);
    	if (bankrupts != Integer.MIN_VALUE){
        	bot.sendMessage(channel, nick+" has gone bankrupt "+bankrupts+" time(s).");
        } else {
        	bot.sendMessage(channel, "No data found for "+nick+".");
        }
    }
    public void showPlayerDebtPayment(Player p, int amount){
    	bot.sendMessage(channel, p.getNickStr()+" has made a debt payment of $"+formatNumber(amount)+". "+p.getNickStr()+"'s debt is now $"+formatNumber(p.getDebt())+".");
    }
    
    /* Player/User output methods to reduce clutter */
	public void infoGameRules(User user) {
		bot.sendNotice(user,getGameRulesStr());
	}
	public void infoGameHelp(User user) {
		bot.sendNotice(user,getGameHelpStr());
	}
    public void infoGameCommands(User user){
    	bot.sendNotice(user,getGameNameStr()+" commands:");
    	bot.sendNotice(user,getGameCommandStr());
    }
    public void infoNumDecks(User user){
		bot.sendNotice(user, "This game of "+getGameNameStr()+" is using "+deck.getNumberDecks()+" deck(s) of cards.");
    }
    public void infoNumCards(User user){
        bot.sendNotice(user, deck.getNumberCards()+" cards left in the deck.");
    }
    public void infoNumDiscards(User user){
        bot.sendNotice(user, deck.getNumberDiscards()+" cards in the discard pile.");
    }
    public void infoWaitRoundEnd(User user){
    	bot.sendNotice(user, "A round is in progress! Wait for the round to end.");
    }
    public void infoRoundStarted(User user){
    	bot.sendNotice(user, "A round is in progress!");
    }
    public void infoNotStarted(User user){
    	bot.sendNotice(user, "No round in progress!");
    }
    public void infoNotBetting(User user){
    	bot.sendNotice(user, "No betting in progress!");
    }
    public void infoNotTurn(User user){
    	bot.sendNotice(user, "It's not your turn!");
    }
    public void infoNoCards(User user){
    	bot.sendNotice(user, "No cards have been dealt yet!");
    }
    public void infoBlacklisted(User user){
    	bot.sendNotice(user, "You have gone bankrupt. Please wait for a loan to join again.");
    }
    public void infoAlreadyJoined(User user){
    	bot.sendNotice(user, "You have already joined!");
    }
    public void infoNotJoined(User user){
    	bot.sendNotice(user, "You are not currently joined!");
    }
    public void infoAlreadyWaiting(User user){
    	bot.sendNotice(user, "You have already joined the waitlist!");
    }
    public void infoOpsOnly(User user){
    	bot.sendNotice(user, "This command may only be used by channel Ops.");
    }
    public void infoNewPlayer(Player p){
    	bot.sendNotice(p.getUser(), "Welcome to "+getGameNameStr()+"! Here's $"+formatNumber(getNewCash())+" to get you started!");
    }
    public void infoNewUser(User user){
    	bot.sendNotice(user, "Welcome to "+getGameNameStr()+"! For help, type .ghelp!");
    }
    public void infoPlayerWaiting(Player p){
    	bot.sendNotice(p.getUser(), "You have joined the waitlist and will be automatically added next round.");
	}
    public void infoPlayerLeaveWaiting(Player p){
    	bot.sendNotice(p.getUser(), "You have left the waitlist and will not be automatically added next round.");
    }
    public void infoPaymentTooLow(Player p){
    	bot.sendNotice(p.getUser(), "Minimum payment is $1. Try again.");
    }
    public void infoPaymentTooHigh(Player p){
    	bot.sendNotice(p.getUser(), "Your outstanding debt is only $"+formatNumber(p.getDebt())+". Try again.");
    }
    public void infoPaymentWillBankrupt(Player p){
    	bot.sendNotice(p.getUser(), "You cannot go bankrupt trying to pay off your debt. Try again.");
    }
    public void infoInsufficientFunds(Player p){
    	bot.sendNotice(p.getUser(), "Insufficient funds. Try again.");
    }
    public void infoNoParameter(User user){
    	bot.sendNotice(user, "Parameter(s) missing!");
    }
    public void infoImproperParameter(User user){
        bot.sendNotice(user,"Improper parameter(s). Try again.");
    }
    public void infoBetTooLow(Player p){
        bot.sendNotice(p.getUser(), "Minimum bet is $1. Try again.");
    }
    public void infoBetTooHigh(Player p){
        bot.sendNotice(p.getUser(), "Maximum bet is $"+formatNumber(p.getCash())+". Try again.");
    }
    
    /* Reveals cards in the deck/discards */
    public void infoDeckCards(User user, char type, int num){
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
        	bot.sendNotice(user, cardStr.substring(0, cardStr.length()-1)+Colors.NORMAL);
        }
    }
    
    /* Parameter handling */   
    public int parseNumberParam(String str){
        StringTokenizer st = new StringTokenizer(str);
        String a;
        a = st.nextToken();
        a = st.nextToken();
        return Integer.parseInt(a);
    }
    public String parseStringParam(String str){
        StringTokenizer st = new StringTokenizer(str);
        String a;
        a = st.nextToken();
        a = st.nextToken();
        return a;
    }
    public String[] parseIniParams(String str){
    	StringTokenizer st = new StringTokenizer(str);
        String a,b;
        a = st.nextToken();
        a = st.nextToken();
        b = st.nextToken();
        String[] out = {a,b};
        return out;
    }
    
    /* Formatted strings */
    abstract public String getGameNameStr();
    abstract public String getGameHelpStr();
    abstract public String getGameRulesStr();
    abstract public String getGameCommandStr();
    public String getWinStr(){
    	return Colors.GREEN+",01"+" WIN "+Colors.NORMAL;
    }
    public String getLossStr(){
    	return Colors.RED+",01"+" LOSS "+Colors.NORMAL;
    }
    public String formatNumber(int n){
    	return String.format("%,d", n);
    }
}