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
import org.pircbotx.hooks.ListenerAdapter;

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
    public static class RespawnTask extends TimerTask{
        Player player;
        CardGame game;
        public RespawnTask(Player p, CardGame g){
            player = p;
            game = g;
        }
        @Override
        public void run(){
            player.setCash(1000);
            game.bot.sendMessage(game.channel, player.getNickStr()+" has been loaned $1000. Please bet responsibly." );
            game.blacklist.remove(player);
            game.savePlayerData(player);
        }
    }
    public static class IdleOutTask extends TimerTask{
        Player player;
        CardGame game;
        public IdleOutTask(Player p, CardGame g){
            player = p;
            game = g;
        }
        @Override
        public void run(){
            if (player == game.getCurrentPlayer()){
                game.bot.sendMessage(game.channel, player.getNickStr()+" has wasted precious time and idled out." );
                game.leaveGame(player.getUser());
            }
        }
    }
    
    protected PircBotX bot; //bot handling the game
    protected Channel channel; //channel where game is being played
    protected String gameName;
    protected ArrayList<Player> players, blacklist, waitlist;
    protected CardDeck shoe;
    protected Player currentPlayer;
    protected boolean inProgress, betting;
    protected Timer idleOutTimer;
    
    public CardGame (PircBotX parent,Channel gameChannel){
        bot = parent;
        channel = gameChannel;
        players = new ArrayList<Player>();
        blacklist = new ArrayList<Player>();
        waitlist = new ArrayList<Player>();
        inProgress = false;
        betting = false;
        checkPlayerFile();
    }
    
    /* Game management methods */
    abstract public void startRound();
    abstract public void endRound();
    abstract public void endGame();
    abstract public void resetGame();
    abstract public void leaveGame(User u);
    abstract public void resetPlayers();
    public void addWaitingPlayers(){
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
    public void setIdleOutTimer(){
        idleOutTimer = new Timer();
        idleOutTimer.schedule(new IdleOutTask(currentPlayer, this), 60000);
    }
    public void cancelIdleOutTimer(){
        idleOutTimer.cancel();
    }
    public void discardPlayerHand(Player p){
        if (p.hasHand()){
            shoe.addToDiscard(p.getHand());
            p.resetHand();
        }
    }

    /* Player management methods */
    public abstract void addPlayer(User user);
    public abstract void addWaitingPlayer(User user);
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
        showPlayerLeaveWaiting(p);
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
    public Player getCurrentPlayer(){
        return currentPlayer;
    }
    public int getPlayerCash(String nick){
    	if (playerJoined(nick)){
    		return findPlayer(nick).getCash();
    	} else {
	    	boolean found = false;
	        try {
	        	BufferedReader f = new BufferedReader(new FileReader("players.txt"));
	            StringTokenizer st;
	            String fnick;
	            String fsimple;
	            int famount=0;
	            while (f.ready()){
	                st = new StringTokenizer(f.readLine());
	                fnick = st.nextToken().toLowerCase();
	                famount = Integer.parseInt(st.nextToken());
	                fsimple = st.nextToken();
	                if (nick.toLowerCase().equals(fnick.toLowerCase())){
	                    found = true;
	                    break;
	                }
	            }
	            f.close();
	            if (found){
	            	return famount;
	            } else {
	            	return -1;
	            }
	        } catch (IOException e){
	        	System.out.println("Error reading players.txt");
	        	return -1;
	        }
    	}
    }
    public void togglePlayerSimple(User u){
        Player p = findPlayer(u);
        p.setSimple(!p.isSimple());
        if (p.isSimple()){
            bot.sendNotice(p.getUser(), "Info will now be noticed to you.");
        } else {
            bot.sendMessage(p.getUser(), "Info will now be messaged to you.");
        }
    }
    public void loadPlayerData(Player p){
        boolean found = false;
        try {
        	BufferedReader f = new BufferedReader(new FileReader("players.txt"));
            StringTokenizer st;
            String nick;
            String simple;
            int amount;
            while (f.ready()){
                st = new StringTokenizer(f.readLine());
                nick = st.nextToken().toLowerCase();
                amount = Integer.parseInt(st.nextToken());
                simple = st.nextToken();
                if (p.getNick().toLowerCase().equals(nick)){
                    p.setCash(amount);
                    if (simple.equals("simple")){
                        p.setSimple(true);
                    } else {
                        p.setSimple(false);
                    }
                    found = true;
                    break;
                }
            }
            f.close();
            if (!found){
                p.setCash(1000);
                p.setSimple(true);
                infoNewPlayer(p);
            }
        } catch (IOException e){
        	System.out.println("Error reading players.txt");
        }
    }
    public void savePlayerData(Player p){
        boolean found = false;
        try {
            ArrayList<String> nicks;
            ArrayList<Integer> amounts;
            ArrayList<String> simples;
        	BufferedReader f = new BufferedReader(new FileReader("players.txt"));
            StringTokenizer st;
            nicks = new ArrayList<String>();
            amounts = new ArrayList<Integer>();
            simples = new ArrayList<String>();
            String nick;
            int amount;
            String simple;
            while (f.ready()){
                st = new StringTokenizer(f.readLine());
                nick = st.nextToken().toLowerCase();
                amount = Integer.parseInt(st.nextToken());
                simple = st.nextToken();
                nicks.add(nick);
                if (p.getNick().toLowerCase().equals(nick)){
                    amounts.add(p.getCash());
                    if (p.isSimple()){
                        simples.add("simple");
                    } else {
                        simples.add("nosimple");
                    }
                    found = true;
                } else {
                    amounts.add(amount);
                    simples.add(simple);
                }
            }
            f.close();
            if (!found){
                nicks.add(p.getNick());
                amounts.add(p.getCash());
                if (p.isSimple()){
                    simples.add("simple");
                } else {
                    simples.add("nosimple");
                }
            }
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("players.txt")));
            for (int ctr = 0; ctr<nicks.size(); ctr++){
                out.println(nicks.get(ctr)+" "+amounts.get(ctr)+" "+simples.get(ctr));
            }
            out.close();
        } catch (IOException e){
        	System.out.println("Error reading/writing players.txt");
        }
    }
    public void checkPlayerFile(){
    	try {
	    	try {
	    		BufferedReader f = new BufferedReader(new FileReader("players.txt"));
	    		f.close();
	    	} catch (FileNotFoundException e){
	            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("players.txt")));
	            out.close();
	        }
    	} catch (IOException e){}
    }
    public boolean isPlayerBankrupt(Player p){
        return (p.getCash() == 0);
    }
    public boolean isBlacklisted(User u){
        for (int ctr=0; ctr<blacklist.size(); ctr++){
            if (blacklist.get(ctr).getNick().equals(u.getNick())){
                return true;
            }
        }
        return false;
    }
    
    /* Channel output methods to reduce clutter */
    public void showPlayerHand(Player p) {
    	bot.sendMessage(channel, p.getNickStr()+": "+p.getCardStr(0));
    }
    public void showPlayerTurn(Player p) {
    	bot.sendMessage(channel,"It's now "+p.getNickStr()+"'s turn.");
    }
    public void showPlayerJoin(Player p){
        bot.sendMessage(channel, p.getNickStr()+" has joined the game.");
    }
    public void showPlayerWaiting(Player p){
    	bot.sendMessage(channel, p.getNickStr()+" has joined the waitlist. S/He will be automatically added next round.");
    }
    public void showPlayerLeave(Player p){
        bot.sendMessage(channel, p.getNickStr()+" has left the game.");
    }
    public void showPlayerLeaveWaiting(Player p){
    	bot.sendMessage(channel, p.getNickStr()+" has left the waitlist. S/He will not be automatically added next round.");
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
        Player p;
        if (getNumberPlayers()==0){
            outStr = "0 players joined!";
        } else {
            outStr = getNumberPlayers()+ " player(s): ";
            for (int ctr=0; ctr<getNumberPlayers(); ctr++){
                p = getPlayer(ctr);
                outStr += p.getNick()+" "; 
            }
        }
        bot.sendMessage(channel, outStr);
    }
    public void showWaiting(){
    	String outStr;
        Player p;
        if (getNumberWaiting()==0){
            outStr = "0 players waiting!";
        } else {
            outStr = getNumberWaiting()+ " player(s) waiting: ";
            for (int ctr=0; ctr < getNumberWaiting(); ctr++){
                p = getWaiting(ctr);
                outStr += p.getNick()+" "; 
            }
        }
        bot.sendMessage(channel, outStr);
    }
    public void showPlayerCash(String nick){
    	int cash = getPlayerCash(nick);
    	if (cash > -1){
        	bot.sendMessage(channel, nick+" has $"+cash+".");
        } else {
        	bot.sendMessage(channel, "No data found for "+nick+".");
        }
    }
    public void showTopPlayers(int n){
    	Player p;
    	int highIndex;
        for (int ctr=0; ctr<getNumberPlayers(); ctr++){
            p = getPlayer(ctr);
            savePlayerData(p);
        }
        try {
        	BufferedReader f = new BufferedReader(new FileReader("players.txt"));
            StringTokenizer st;
            ArrayList<String> fnick = new ArrayList<String>();
            String fsimple;
            ArrayList<Integer> famount = new ArrayList<Integer>();
            while (f.ready()){
                st = new StringTokenizer(f.readLine());
                fnick.add(st.nextToken());
                famount.add(Integer.parseInt(st.nextToken()));
                fsimple = st.nextToken();
            }
            f.close();
            bot.sendMessage(channel, Colors.DARK_GREEN+Colors.BOLD+"Top "+n+" Players:"+Colors.NORMAL);
            for (int ctr=1; ctr<=3; ctr++){
            	highIndex = 0;
            	for (int ctr2=0; ctr2<fnick.size(); ctr2++){
            		if (famount.get(ctr2) > famount.get(highIndex)){
            			highIndex = ctr2;
            		}
            	}
            	bot.sendMessage(channel, ctr+". "+fnick.get(highIndex)+": $"+famount.get(highIndex));
            	fnick.remove(highIndex);
            	famount.remove(highIndex);
            	if (fnick.isEmpty()){
            		break;
            	}
            }
        } catch (IOException e){
        	System.out.println("Error reading players.txt");
        }
    }
    
    /* Player/User output methods to reduce clutter */
    abstract public void infoGameRules(User user);
    abstract public void infoGameHelp(User user);
    public void infoPlayerHand(User user){
        Player p = findPlayer(user);
        if (p.isSimple()){
            bot.sendNotice(user, "Your cards are "+p.getCardStr(0)+".");
        } else {
            bot.sendMessage(user, "Your cards are "+p.getCardStr(0)+".");
        }
    }
    public void infoPlayerBet(User user){
        Player p = findPlayer(user);
        if (p.isSimple()){
            bot.sendNotice(user, "You have bet $"+p.getBet()+".");
        } else {
            bot.sendMessage(user, "You have bet $"+p.getBet()+".");
        }
    }
    public void infoPlayerBankrupt(User user){
        Player p = findPlayer(user);
        if (p.isSimple()){
            bot.sendNotice(p.getUser(), "You've lost all your money. Please wait 3 minutes for a loan.");
        } else {
            bot.sendMessage(p.getUser(), "You've lost all your money. Please wait 3 minutes for a loan.");
        }
    }
    public void infoGameCommands(User user){
    	if (playerJoined(user)){
			Player p = findPlayer(user);
		    if (p.isSimple()){
		    	bot.sendNotice(user,getGameNameStr()+" commands:");
		    	bot.sendNotice(user,getGameCommandStr());
		    } else {
		    	bot.sendMessage(user,getGameNameStr()+" commands:");
		    	bot.sendMessage(user,getGameCommandStr());
		    }
    	} else {
    		bot.sendMessage(user,getGameNameStr()+" commands:");
	    	bot.sendMessage(user,getGameCommandStr());
    	}
    }
    public void infoNumDecks(User user){
    	if (playerJoined(user)){
			Player p = findPlayer(user);
		    if (p.isSimple()){
		    	bot.sendNotice(user, "This game of "+getGameNameStr()+" is using "+shoe.getNumberDecks()+" deck(s) of cards.");
		    } else {
		    	bot.sendMessage(user, "This game of "+getGameNameStr()+" is using "+shoe.getNumberDecks()+" deck(s) of cards.");
		    }
    	} else {
    		bot.sendMessage(user, "This game of "+getGameNameStr()+" is using "+shoe.getNumberDecks()+" deck(s) of cards.");
    	}
    }
    public void infoNewPlayer(Player p){
    	bot.sendNotice(p.getUser(), "Welcome to "+getGameNameStr()+"! Here's $1000 to get you started!");
    }
    
    /* Debugging methods for ops */
    public void infoDeckCards(User user, char type, int num){
    	int cardIndex=0, numOut, n;
    	String cardStr;
    	ArrayList<Card> tCards;
    	if (type == 'c'){
    		tCards = shoe.getCards();
    		if (num > shoe.getNumberCards()){
        		n = shoe.getNumberCards();
        	} else {
        		n = num;
        	}
    	} else {
    		tCards = shoe.getDiscards();
    		if (num > shoe.getNumberDiscards()){
        		n = shoe.getNumberDiscards();
        	} else {
        		n = num;
        	}
    	}
        while(n > 0){
        	cardStr = cardIndex+"";
        	if (n > 25){
        		cardStr += "-"+(cardIndex+25-1)+": ";
        		numOut = 25;
        		n -= 25;
        	} else {
        		cardStr += "-"+(cardIndex+n-1)+": ";
        		numOut = n;
        		n -= n;
        	}
        	for (int ctr=cardIndex; ctr < cardIndex+numOut; ctr++){
    			cardStr += tCards.get(ctr)+" ";
    		}
        	cardIndex+=numOut;
        	bot.sendNotice(user, cardStr.substring(0, cardStr.length()-1)+Colors.NORMAL);
        }
    }
    public void infoNumCards(User user){
        bot.sendNotice(user, shoe.getNumberCards()+" cards left in the dealer's shoe.");
    }
    public void infoNumDiscards(User user){
        bot.sendNotice(user, shoe.getNumberDiscards()+" cards in the discard pile.");
    }
    
    /* IRC parameter handling integers */   
    public int parseNumberParam(String str){
        StringTokenizer st = new StringTokenizer(str);
        String a;
        a = st.nextToken();
        a = st.nextToken();
        return Integer.parseInt(a);
    }
    /* IRC parameter handling for strings */   
    public String parseStringParam(String str){
        StringTokenizer st = new StringTokenizer(str);
        String a;
        a = st.nextToken();
        a = st.nextToken();
        return a;
    }    
    
    /* Formatted strings */
    abstract public String getGameNameStr();
    abstract public String getGameCommandStr();
}