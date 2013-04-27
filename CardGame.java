/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
    protected ArrayList<Player> players, blacklist;
    protected CardDeck deck;
    protected Player currentPlayer;
    protected boolean inProgress, betting;
    protected Timer idleOutTimer;
    
    public CardGame (PircBotX parent,Channel gameChannel){
        bot = parent;
        channel = gameChannel;
        players = new ArrayList<Player>();
        blacklist = new ArrayList<Player>();
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
            deck.addToDiscard(p.getHand());
            p.resetHand();
        }
    }

    /* Player management methods */
    public abstract void addPlayer(User u);
    public boolean playerJoined(User u){
        Player p = findPlayer(u);
        if (p != null){
            return true;
        }
        return false;
    }
    public void removePlayer(User u){
        Player p = findPlayer(u);
        discardPlayerHand(p);
        players.remove(p);
        savePlayerData(p);
        showPlayerLeave(p);
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
    public Player findPlayer(User u){
        for (int ctr=0; ctr<players.size(); ctr++){
            if (players.get(ctr).getNick().equals(u.getNick())){
                return players.get(ctr);
            }  
        }
        return null;
    }
    public int getNumberPlayers(){
        return players.size();
    }
    public Player getCurrentPlayer(){
        return currentPlayer;
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
        Player p;
        if (getNumberPlayers()==0){
            outStr = "0 players joined!";
        } else {
            outStr = getNumberPlayers()+ " player(s): ";
            for (int ctr=0; ctr<players.size(); ctr++){
                p = players.get(ctr);
                outStr += p.getNick()+" "; 
            }
        }
        bot.sendMessage(channel, outStr);
    }
    
    /* Player/User output methods to reduce clutter */
    public void infoPlayerHand(User user){
        Player p = findPlayer(user);
        if (p.isSimple()){
            bot.sendNotice(user, "Your cards are "+p.getCardStr(0)+".");
        } else {
            bot.sendMessage(user, "Your cards are "+p.getCardStr(0)+".");
        }
    }
    public void infoPlayerCash(User user){
        Player p = findPlayer(user);
        if (p.isSimple()){
            bot.sendNotice(user, "You have $"+p.getCash()+".");
        } else {
            bot.sendMessage(user, "You have $"+p.getCash()+".");
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
		    	bot.sendNotice(user, "This game of "+getGameNameStr()+" is using "+deck.getNumberDecks()+" deck(s) of cards.");
		    } else {
		    	bot.sendMessage(user, "This game of "+getGameNameStr()+" is using "+deck.getNumberDecks()+" deck(s) of cards.");
		    }
    	} else {
    		bot.sendMessage(user, "This game of "+getGameNameStr()+" is using "+deck.getNumberDecks()+" deck(s) of cards.");
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
    		tCards = deck.getCards();
    		if (num > deck.getNumberCards()){
        		n = deck.getNumberCards();
        	} else {
        		n = num;
        	}
    	} else {
    		tCards = deck.getDiscards();
    		if (num > deck.getNumberDiscards()){
        		n = deck.getNumberDiscards();
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
        bot.sendNotice(user, deck.getNumberCards()+" cards left in the deck.");
    }
    public void infoNumDiscards(User user){
        bot.sendNotice(user, deck.getNumberDiscards()+" cards in the discard pile.");
    }
    
    /* IRC string handling */   
    public int parseNumberParam(String str){
        StringTokenizer st = new StringTokenizer(str);
        String a;
        a = st.nextToken();
        a = st.nextToken();
        return Integer.parseInt(a);
    }
    
    /* Formatted strings */
    abstract public String getGameNameStr();
    abstract public String getGameCommandStr();
}