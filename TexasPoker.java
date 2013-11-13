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

public class TexasPoker extends CardGame{
    /* A pot class to handle bets and payouts in Texas Hold'em Poker. */
    private class PokerPot {
        private ArrayList<PokerPlayer> players;
        private ArrayList<PokerPlayer> donors;
        private int pot;

        public PokerPot(){
            pot = 0;
            players = new ArrayList<PokerPlayer>();
            donors = new ArrayList<PokerPlayer>();
        }

        public int getPot(){
            return pot;
        }
        public void addPot(int amount){
            pot += amount;
        }
        public void addPlayer(PokerPlayer p){
            players.add(p);
        }
        public void removePlayer(PokerPlayer p){
            players.remove(p);
        }
        public void addDonor(PokerPlayer p) {
            donors.add(p);
        }
        public void removeDonor(PokerPlayer p) {
            donors.remove(p);
        }
        public PokerPlayer getPlayer(int c){
            return players.get(c);
        }
        public ArrayList<PokerPlayer> getPlayers(){
            return players;
        }
        public PokerPlayer getDonor(int c) {
            return donors.get(c);
        }
        public ArrayList<PokerPlayer> getDonors() {
            return donors;
        }
        public boolean hasPlayer(PokerPlayer p){
            return players.contains(p);
        }
        public boolean hasDonor(PokerPlayer p) {
            return donors.contains(p);
        }
        public int getNumPlayers(){
            return players.size();
        }
        public int getNumDonors() {
            return donors.size();
        }
    }
    
    /* Nested class to store statistics, based on number of decks used, for the house */
    private class HouseStat extends Stats {
        private ArrayList<PokerPlayer> donors;
        private ArrayList<PokerPlayer> winners;
        
        public HouseStat() {
            this(0);
        }
        
        public HouseStat(int pot) {
            set("biggestpot", pot);
            donors = new ArrayList<PokerPlayer>();
            winners = new ArrayList<PokerPlayer>();
        }
        
        public int getNumDonors() {
            return donors.size();
        }
        public void addDonor(PokerPlayer p){
            donors.add(p);
        }
        public void clearDonors(){
            donors.clear();
        }
        public int getNumWinners() {
            return winners.size();
        }
        public void addWinner(PokerPlayer p){
            winners.add(p);
        }
        public void clearWinners(){
            winners.clear();
        }
        
        public String getDonorsString(){
            String outStr = "";
            for (int ctr = 0; ctr < donors.size(); ctr++){
                outStr += donors.get(ctr).getNick() + " ";
            }
            return outStr.substring(0, outStr.length() - 1);
        }
        
        public String getWinnersString(){
            String outStr = "";
            for (int ctr = 0; ctr < winners.size(); ctr++){
                outStr += winners.get(ctr).getNick() + " ";
            }
            return outStr.substring(0, outStr.length() - 1);
        }
        
        public String getToStringList(){
            String outStr;
            int size = donors.size();
            if (size == 0){
                outStr = formatBold("0") + " players";
            } else if (size == 1){
                outStr = formatBold("1") + " player: " + donors.get(0).getNickStr();
            } else {
                outStr = formatBold(size) + " players: ";
                for (int ctr = 0; ctr < size; ctr++){
                    if (ctr == size-1){
                        if (winners.contains(donors.get(ctr))) {
                            outStr += donors.get(ctr).getNickStr();
                        } else {
                            outStr += donors.get(ctr).getNick();
                        }
                    } else {
                        if (winners.contains(donors.get(ctr))) {
                            outStr += donors.get(ctr).getNickStr() + ", ";
                        } else {
                            outStr += donors.get(ctr).getNick() + ", ";
                        }
                    }
                }   
            }
            return outStr;
        }
        
        @Override
        public String toString() {
            return "Biggest pot: $" + formatNumber(get("biggestpot")) + " (" + getToStringList() + ").";
        }
    }
    
    private ArrayList<PokerPot> pots;
    private PokerPot currentPot;
    private PokerPlayer dealer, smallBlind, bigBlind, topBettor;
    private Hand community;
    private HouseStat house;

    /**
     * Constructor for TexasPoker, subclass of CardGame
     * 
     * @param parent The bot that uses an instance of this class
     * @param commChar The command char
     * @param gameChannel The IRC channel in which the game is to be run.
     */
    public TexasPoker(CasinoBot parent, char commChar, Channel gameChannel){
        super(parent, commChar, gameChannel);
        setGameName("Texas Hold'em Poker");
        setIniFile("texaspoker.ini");
        loadLib(helpMap, "texaspoker.help");
        loadLib(msgMap, "msglib.txt");
        house = new HouseStat();
        loadHouseStats();
        initialize();
        loadIni();
        deck = new CardDeck();
        deck.shuffleCards();
        pots = new ArrayList<PokerPot>();
        community = new Hand();
        currentPot = null;
        dealer = null;
        smallBlind = null;
        bigBlind = null;
        topBettor = null;
        showMsg(getMsg("game_start"), getGameNameStr());
    }
    
    /* Command management method */
    @Override
    public void processCommand(User user, String command, String[] params){
        String nick = user.getNick();
        String hostmask = user.getHostmask();
        
        /* Check if it's a common command */
        super.processCommand(user, command, params);
        
        /* Parsing commands from the channel */
        if (command.equals("join") || command.equals("j")) {
            if (bot.bjgame != null &&
                (bot.bjgame.isJoined(nick) || bot.bjgame.isWaitlisted(nick))){
                bot.sendNotice(user, "You're already joined in "+bot.bjgame.getGameNameStr()+"!");
            } else if (bot.bjgame != null && bot.bjgame.isBlacklisted(nick)){
                informPlayer(nick, getMsg("blacklisted"));
            } else{
                join(nick, hostmask);
            }
        } else if (command.equals("start") || command.equals("go")) {
            if (isStartAllowed(nick)){
                if (params.length > 0){
                    try {
                        set("startcount", Math.min(get("autostarts") - 1, Integer.parseInt(params[0]) - 1));
                    } catch (NumberFormatException e) {
                        // Do nothing and proceed
                    }
                }
                set("inprogress", 1);
                showStartRound();
                setStartRoundTask();
            }
        } else if (command.equals("bet") || command.equals("b")) {
            if (isPlayerTurn(nick)){
                if (params.length > 0){
                    try {
                        bet(Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equals("c") || command.equals("ca") || command.equals("call")) {
            if (isPlayerTurn(nick)){
                call();
            }
        } else if (command.equals("x") || command.equals("ch") || command.equals("check")) {
            if (isPlayerTurn(nick)){
                check();
            }
        } else if (command.equals("fold") || command.equals("f")) {
            if (isPlayerTurn(nick)){
                fold();
            }
        } else if (command.equals("raise") || command.equals("r")) {
            if (isPlayerTurn(nick)){
                if (params.length > 0){
                    try {
                        bet(Integer.parseInt(params[0]) + get("currentbet"));
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equals("allin") || command.equals("a")){
            if (isPlayerTurn(nick)){
                bet(currentPlayer.get("cash"));
            }
        } else if (command.equals("community") || command.equals("comm")){
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else if (!has("inprogress")) {
                informPlayer(nick, getMsg("no_start"));
            } else if (!has("stage")){
                informPlayer(nick, getMsg("no_community"));
            } else {
                showCommunityCards();
            }
        } else if (command.equals("hand")) {
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else if (!has("inprogress")) {
                informPlayer(nick, getMsg("no_start"));
            } else {
                PokerPlayer p = (PokerPlayer) findJoined(nick);
                infoPlayerHand(p, p.getHand());
            }
        } else if (command.equals("turn")) {
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else if (!has("inprogress")) {
                informPlayer(nick, getMsg("no_start"));
            } else {
                showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), currentPlayer.get("bet"), 
                        currentPlayer.get("cash")-currentPlayer.get("bet"), get("currentbet"));
            }
        } else if (command.equals("players")) {
            if (has("inprogress")){
                showTablePlayers();
            } else {
                showMsg(getMsg("players"), getPlayerListString(joined));
            }
        /* Op commands */
        } else if (command.equals("fstart") || command.equals("fgo")){
            if (isForceStartAllowed(user,nick)){
                set("inprogress", 1);
                showStartRound();
                setStartRoundTask();
            }
        } else if (command.equals("fstop")){
            // Use only as last resort. Data will be lost.
            if (isForceStopAllowed(user,nick)){
                PokerPlayer p;
                cancelIdleOutTask();
                for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
                    p = (PokerPlayer) getJoined(ctr);
                    resetPlayer(p);
                }
                resetGame();
                showMsg(getMsg("end_round"), getGameNameStr(), commandChar);
                set("inprogress", 0);
            }
        } else if (command.equals("fj") || command.equals("fjoin")){
            if (!channel.isOp(user)) {
                informPlayer(nick, getMsg("ops_only"));
            } else {
                if (params.length > 0){
                    String fNick = params[0];
                    Set<User> chanUsers = channel.getUsers();
                    Iterator<User> it = chanUsers.iterator();
                    while(it.hasNext()){
                        User u = it.next();
                        if (u.getNick().equalsIgnoreCase(fNick)){
                            // Check if fNick is joined in another game
                            if (bot.bjgame != null &&
                                (bot.bjgame.isJoined(fNick) || bot.bjgame.isWaitlisted(fNick))){
                                bot.sendNotice(user, u.getNick()+" is already joined in "+bot.bjgame.getGameNameStr()+"!");
                            } else if (bot.bjgame != null && bot.bjgame.isBlacklisted(fNick)){
                                bot.sendNotice(user, u.getNick()+" is bankrupt and cannot join!");
                            } else{
                                join(u.getNick(), u.getHostmask());
                            }
                            return;
                        }
                    }
                    informPlayer(nick, getMsg("nick_not_found"), fNick);
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equals("fb") || command.equals("fbet")){
            if (isForcePlayAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        bet(Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equals("fa") || command.equals("fallin")){
            if (isForcePlayAllowed(user, nick)){
                bet(currentPlayer.get("cash"));
            }
        } else if (command.equals("fr") || command.equals("fraise")){
            if (isForcePlayAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        bet(Integer.parseInt(params[0]) + get("currentbet"));
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equals("fc") || command.equals("fca") || command.equals("fcall")){
            if (isForcePlayAllowed(user, nick)){
                call();
            }
        } else if (command.equals("fx") || command.equals("fch") || command.equals("fcheck")){
            if (isForcePlayAllowed(user, nick)){
                check();
            }
        } else if (command.equals("ff") || command.equals("ffold")){
            if (isForcePlayAllowed(user, nick)){
                fold();
            }
        } else if (command.equals("shuffle")){
            if (isOpCommandAllowed(user, nick)){
                shuffleDeck();
            }
        } else if (command.equals("reload")) {
            if (isOpCommandAllowed(user, nick)){
                loadIni();
                showMsg(getMsg("reload_ini"), getIniFile());
            }
        } else if (command.equals("test1")){
            // 1. Test if game will properly determine winner of 2-5 players
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        ArrayList<PokerPlayer> peeps = new ArrayList<PokerPlayer>();
                        PokerPlayer p;
                        PokerHand ph;
                        int winners = 1;
                        int number = Integer.parseInt(params[0]);
                        if (number > 5 || number < 2){
                            throw new NumberFormatException();
                        }
                        // Generate new players
                        for (int ctr=0; ctr < number; ctr++){
                            p = new PokerPlayer(ctr+1+"", "");
                            peeps.add(p);
                            dealHand(p);                            
                            bot.sendMessage(channel, "Player "+p.getNickStr()+": "+p.getHand().toString());
                        }
                        // Generate community cards
                        Hand comm = new Hand();
                        for (int ctr=0; ctr<5; ctr++){
                            dealCard(comm);
                        }
                        bot.sendMessage(channel, "Community: "+comm.toString());
                        // Propagate poker hands
                        for (int ctr=0; ctr < number; ctr++){
                            p = peeps.get(ctr);
                            ph = p.getPokerHand();
                            ph.addAll(p.getHand());
                            ph.addAll(comm);
                            Collections.sort(ph.getAllCards());
                            Collections.reverse(ph.getAllCards());
                            ph.getValue();
                        }
                        // Sort hands in descending order
                        Collections.sort(peeps);
                        Collections.reverse(peeps);
                        // Determine number of winners
                        for (int ctr=1; ctr < peeps.size(); ctr++){
                            if (peeps.get(0).compareTo(peeps.get(ctr)) == 0){
                                winners++;
                            } else {
                                break;
                            }
                        }
                        // Output poker hands with winners listed
                        for (int ctr=0; ctr < winners; ctr++){
                            p = peeps.get(ctr);
                            ph = p.getPokerHand();
                            bot.sendMessage(channel, "Player "+p.getNickStr()+
                                    " ("+p.getHand()+"), "+ph.getName()+": " + ph+" (WINNER)");
                        }
                        for (int ctr=winners; ctr < peeps.size(); ctr++){
                            p = peeps.get(ctr);
                            ph = p.getPokerHand();
                            bot.sendMessage(channel, "Player "+p.getNickStr()+
                                    " ("+p.getHand()+"), "+ph.getName()+": " + ph);
                        }
                        // Discard and shuffle
                        for (int ctr=0; ctr < number; ctr++){
                            resetPlayer(peeps.get(ctr));
                        }
                        deck.addToDiscard(comm.getAllCards());
                        comm.clear();
                        deck.refillDeck();
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }                
            }
        } else if (command.equals("test2")){	
            // 2. Test if arbitrary hands will be scored properly
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        int number = Integer.parseInt(params[0]);
                        if (number > 52 || number < 5){
                            throw new NumberFormatException();
                        }
                        PokerHand h = new PokerHand();
                        for (int ctr = 0; ctr < number; ctr++){
                            dealCard(h);
                        }
                        bot.sendMessage(channel, h.toString(0, h.getSize()));
                        Collections.sort(h.getAllCards());
                        Collections.reverse(h.getAllCards());
                        h.getValue();
                        bot.sendMessage(channel, h.getName()+": " + h);
                        deck.addToDiscard(h.getAllCards());
                        h.clear();
                        deck.refillDeck();
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
                
            }
        }
    }

    /* Game management methods */
    @Override
    public void addPlayer(String nick, String hostmask) {
        addPlayer(new PokerPlayer(nick, hostmask));
    }
    @Override
    public void addWaitlistPlayer(String nick, String hostmask) {
        Player p = new PokerPlayer(nick, hostmask);
        waitlist.add(p);
        informPlayer(p.getNick(), getMsg("join_waitlist"));
    }
    public Player getPlayerAfter(Player p){
        return getJoined((getJoinedIndex(p)+1) % getNumberJoined());
    }
    @Override
    public void startRound() {
        if (getNumberJoined() < 2){
            set("startcount", 0);
            endRound();
        } else {
            setButton();
            showTablePlayers();
            dealTable();
            setBlindBets();
            currentPlayer = getPlayerAfter(bigBlind);
            showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), currentPlayer.get("bet"), 
                        currentPlayer.get("cash")-currentPlayer.get("bet"), get("currentbet"));
            setIdleOutTask();
        }
    }
    @Override
    public void continueRound() {
        // Store currentPlayer as firstPlayer and find the next player
        Player firstPlayer = currentPlayer;
        currentPlayer = getPlayerAfter(currentPlayer);
        PokerPlayer p = (PokerPlayer) currentPlayer;
        
        /*
         * Look for a player who can bet that is not the firstPlayer or the
         * topBettor. If we reach the firstPlayer or topBettor then stop
         * looking.
         */
        while ((p.has("fold") || p.has("allin")) && currentPlayer != firstPlayer
                        && currentPlayer != topBettor) {
            currentPlayer = getPlayerAfter(currentPlayer);
            p = (PokerPlayer) currentPlayer;
        }
        
        /* If we reach the firstPlayer or topBettor, then we have reached the
         * end of a round of betting and we should deal community cards. */
        if (currentPlayer == topBettor || currentPlayer == firstPlayer) {
            // Reset minimum raise
            set("minraise", get("minbet"));
            // Add bets from this round of betting to the pot
            addBetsToPot();
            increment("stage");
            
            // If all community cards have been dealt, move to end of round
            if (get("stage") == 4){
                endRound();
            // Otherwise, deal community cards
            } else {
                /* 
                * If fewer than two players can bet and there are more
                * than 1 non-folded player remaining, only show hands once
                * before dealing the rest of the community cards. Adds a
                * 10-second delay in between each line.
                */
                if (getNumberCanBet() < 2 && getNumberNotFolded() > 1) {
                    if (currentPlayer == topBettor){
                        ArrayList<PokerPlayer> players;
                        players = pots.get(0).getPlayers();
                        String showdownStr = formatHeader(" Showdown: ") + " ";
                        for (int ctr = 0; ctr < players.size(); ctr++) {
                            p = players.get(ctr);
                            showdownStr += p.getNickStr() + " (" + p.getHand() + ")";
                            if (ctr < players.size()-1){
                                showdownStr += ", ";
                            }
                        }
                        bot.sendMessage(channel, showdownStr);
                    }
                   
                   // Add a 10 second delay for dramatic effect
                   try { Thread.sleep(10000); } catch (InterruptedException e){}
                }
                // Burn a card before turn and river
                if (get("stage") != 1) {
                    burnCard();
                }
                dealCommunity();
                /* Only show dealt community cards when there are 
                 * more than 1 non-folded player remaining. */
                if (getNumberNotFolded() > 1){
                    showCommunityCards();
                }
                topBettor = null;
                /* Set the currentPlayer to be dealer to determine who bets
                 * first in the next round of betting. */
                currentPlayer = dealer;
                continueRound();
            }
        // Continue round, if less than 2 players can bet
        } else if (getNumberCanBet() < 2 && topBettor == null){
            continueRound();
        // Continue to the next bettor
        } else {
            showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), currentPlayer.get("bet"), 
                        currentPlayer.get("cash")-currentPlayer.get("bet"), get("currentbet"));
            setIdleOutTask();
        }
    }
    @Override
    public void endRound() {
        set("endround", 1);
        PokerPlayer p;

        // Check if anybody left during post-start waiting period
        if (getNumberJoined() > 1 && pots.size() > 0) {
            String netResults = Colors.YELLOW + ",01 Change: ";
            // Give all non-folded players the community cards
            for (int ctr = 0; ctr < getNumberJoined(); ctr++){
                p = (PokerPlayer) getJoined(ctr);
                if (!p.has("fold")){
                    p.getPokerHand().addAll(p.getHand());
                    p.getPokerHand().addAll(community);
                    Collections.sort(p.getPokerHand().getAllCards());
                    Collections.reverse(p.getPokerHand().getAllCards());
                }
            }

            // Determine the winners of each pot
            showResults();

            /* Clean-up tasks
             * 1. Increment the number of rounds played for player
             * 2. Remove players who have gone bankrupt and set respawn timers
             * 3. Remove players who have quit mid-round
             * 4. Save player data
             * 5. Reset the player
             */
            for (int ctr = 0; ctr < getNumberJoined(); ctr++){
                p = (PokerPlayer) getJoined(ctr);
                p.increment("tprounds");
                
                // Bankrupts
                if (!p.has("cash")) {
                    // Make a withdrawal if the player has a positive bank
                    if (p.get("bank") > 0){
                        int amount = Math.min(p.get("bank"), get("cash"));
                        p.bankTransfer(-amount);
                        savePlayerData(p);
                        informPlayer(p.getNick(), getMsg("auto_withdraw"), amount);
                        // Check if the player has quit
                        if (p.has("quit")){
                            removeJoined(p);
                            ctr--;
                        }
                    // Give penalty to players with no cash in their bank
                    } else {
                        p.increment("bankrupts");
                        blacklist.add(p);
                        removeJoined(p);
                        setRespawnTask(p);
                        ctr--;
                    }
                // Quitters
                } else if (p.has("quit")) {
                    removeJoined(p);
                    ctr--;
                // Remaining players
                } else {
                    savePlayerData(p);
                }
                
                // Add player to list of net results
                if (p.get("change") > 0) {
                    netResults += Colors.WHITE + ",01 " + p.getNick() + " (" + Colors.GREEN + ",01$" + p.get("change") + Colors.WHITE + ",01), ";
                } else if (p.get("change") < 0) {
                    netResults += Colors.WHITE + ",01 " + p.getNick() + " (" + Colors.RED + ",01$" + p.get("change") + Colors.WHITE + ",01), ";
                } else {
                    netResults += Colors.WHITE + ",01 " + p.getNick() + " (" + Colors.WHITE + ",01$" + p.get("change") + Colors.WHITE + ",01), ";
                }
                
                resetPlayer(p);
            }
            bot.sendMessage(channel, netResults.substring(0, netResults.length()-2));
        } else {
            showMsg(getMsg("no_players"));
        }
        resetGame();
        showMsg(getMsg("end_round"), getGameNameStr(), commandChar);
        mergeWaitlist();
        // Check if auto-starts remaining
        if (get("startcount") > 0){
            decrement("startcount");
            if (!has("inprogress")){
                if (getNumberJoined() > 1) {
                    set("inprogress", 1);
                    showStartRound();
                    setStartRoundTask();
                } else {
                    set("startcount", 0);
                }
            }
        }
    }
    @Override
    public void endGame() {
        cancelStartRoundTask();
        cancelIdleOutTask();
        cancelRespawnTasks();
        gameTimer.cancel();
        deck = null;
        community = null;
        pots.clear();
        currentPot = null;
        currentPlayer = null;
        dealer = null;
        smallBlind = null;
        bigBlind = null;
        topBettor = null;
        house = null;
        devoiceAll();
        showMsg(getMsg("game_end"), getGameNameStr());
        joined.clear();
        waitlist.clear();
        blacklist.clear();
        helpMap.clear();
        msgMap.clear();
        settingsMap.clear();
        bot = null;
        channel = null;
    }
    @Override
    public void resetGame() {
        set("stage", 0);
        set("currentbet", 0);
        set("inprogress", 0);
        set("endround", 0);
        discardCommunity();
        currentPot = null;
        pots.clear();
        currentPlayer = null;
        bigBlind = null;
        smallBlind = null;
        topBettor = null;
        deck.refillDeck();
    }
    @Override
    public void leave(String nick) {
        // Check if the nick is even joined
        if (isJoined(nick)){
            PokerPlayer p = (PokerPlayer) findJoined(nick);
            // Check if a round is in progress
            if (has("inprogress")) {
                /* If still in the post-start waiting phase, then currentPlayer has
                 * not been set yet. */
                if (currentPlayer == null){
                    removeJoined(p);
                // Check if it is already in the endRound stage
                } else if (has("endround")){
                    p.set("quit", 1);
                    bot.sendNotice(p.getNick(), "You will be removed at the end of the round.");
                // Force the player to fold if it is his turn
                } else if (p == currentPlayer){
                    p.set("quit", 1);
                    bot.sendNotice(p.getNick(), "You will be removed at the end of the round.");
                    fold();
                } else {
                    p.set("quit", 1);
                    bot.sendNotice(p.getNick(), "You will be removed at the end of the round.");
                    if (!p.has("fold")){
                        p.set("fold", 1);
                        showMsg(getMsg("tp_fold"), p.getNickStr(), p.get("cash")-p.get("bet"));
                        // Remove this player from any existing pots
                        if (currentPot != null && currentPot.hasPlayer(p)){
                            currentPot.removePlayer(p);
                        }
                        for (int ctr = 0; ctr < pots.size(); ctr++){
                            PokerPot cPot = pots.get(ctr);
                            if (cPot.hasPlayer(p)){
                                cPot.removePlayer(p);
                            }
                        }
                        // If there is only one player who hasn't folded,
                        // force call on that remaining player (whose turn it must be)
                        if (getNumberNotFolded() == 1){
                            call();
                        }
                    }
                }
            // Just remove the player from the joined list if no round in progress
            } else {
                removeJoined(p);
            }
        // Check if on the waitlist
        } else if (isWaitlisted(nick)) {
            informPlayer(nick, getMsg("leave_waitlist"));
            removeWaitlisted(nick);
        } else {
            informPlayer(nick, getMsg("no_join"));
        }
    }
    public void resetPlayer(PokerPlayer p) {
        discardPlayerHand(p);
        p.clear("fold");
        p.clear("quit");
        p.clear("allin");
        p.clear("change");
    }
    public void setButton(){
        if (dealer == null){
            dealer = (PokerPlayer) getJoined(0);
        } else {
            dealer = (PokerPlayer) getPlayerAfter(dealer);
        }
        if (getNumberJoined() == 2){
            smallBlind = dealer;
        } else {
            smallBlind = (PokerPlayer) getPlayerAfter(dealer);
        }
        bigBlind = (PokerPlayer) getPlayerAfter(smallBlind);
    }
    public void setBlindBets(){
        // Set the small blind to minimum raise or the player's cash, 
        // whichever is less.
        smallBlind.set("bet", Math.min(get("minbet")/2, smallBlind.get("cash")));
        // Set the big blind to minimum raise + small blind or the player's 
        // cash, whichever is less.
        bigBlind.set("bet", Math.min(get("minbet"), bigBlind.get("cash")));
        // Set the current bet to the bigger of the two blinds.
        set("currentbet", Math.max(smallBlind.get("bet"), bigBlind.get("bet")));
        set("minraise", get("minbet"));
    }
    
    /* Game command logic checking methods */
    public boolean isStartAllowed(String nick){
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (has("inprogress")) {
            informPlayer(nick, getMsg("round_started"));
        } else if (getNumberJoined() < 2) {
            showMsg(getMsg("no_players"));
        } else {
            return true;
        }
        return false;
    }
    public boolean isPlayerTurn(String nick){
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else if (!has("inprogress")) {
            informPlayer(nick, getMsg("no_start"));
        } else if (findJoined(nick) != currentPlayer){
            informPlayer(nick, getMsg("wrong_turn"));
        } else {
            return true;
        }
        return false;
    }
    public boolean isForceStartAllowed(User user, String nick){
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (has("inprogress")) {
            informPlayer(nick, getMsg("round_started"));
        } else if (getNumberJoined() < 2) {
            showMsg(getMsg("no_players"));
        } else {
            return true;
        }
        return false;
    }
    public boolean isForceStopAllowed(User user, String nick){
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!has("inprogress")) {
            informPlayer(nick, getMsg("no_start"));
        } else {
            return true;
        }
        return false;
    }
    public boolean isForcePlayAllowed(User user, String nick){
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!has("inprogress")) {
            informPlayer(nick, getMsg("no_start"));
        } else {
            return true;
        }
        return false;
    }
    
    /* Game settings management */
    @Override
    protected final void initialize(){
        super.initialize();
        // Do not use set()
        // Ini file settings
        settingsMap.put("cash", 1000);
        settingsMap.put("idle", 60);
        settingsMap.put("idlewarning", 45);
        settingsMap.put("respawn", 600);
        settingsMap.put("maxplayers", 22);
        settingsMap.put("minbet", 10);
        settingsMap.put("autostarts", 10);
        // In-game properties
        settingsMap.put("stage", 0);
        settingsMap.put("currentbet", 0);
        settingsMap.put("minraise", 0);
    }
    @Override
    protected final void saveIniFile() {
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(getIniFile())));
            out.println("#Settings");
            out.println("#Number of seconds before a player idles out");
            out.println("idle=" + get("idle"));
            out.println("#Number of seconds before a player is given a warning for idling");
            out.println("idlewarning=" + get("idlewarning"));
            out.println("#Initial amount given to new and bankrupt players");
            out.println("cash=" + get("cash"));
            out.println("#Number of seconds before a bankrupt player is allowed to join again");
            out.println("respawn=" + get("respawn"));
            out.println("#Minimum bet (big blind), preferably an even number");
            out.println("minbet=" + get("minbet"));
            out.println("#The maximum number of players allowed to join a game");
            out.println("maxplayers=" + get("maxplayers"));
            out.println("#The maximum number of autostarts allowed");
            out.println("autostarts=" + get("autostarts"));
            out.close();
        } catch (IOException e) {
            bot.log("Error creating " + getIniFile() + "!");
        }
    }
    
    /* House stats management */
    public final void loadHouseStats() {
        try {
            BufferedReader in = new BufferedReader(new FileReader("housestats.txt"));
            String str;
            int biggestpot, players, winners;
            StringTokenizer st;
            while (in.ready()) {
                str = in.readLine();
                if (str.startsWith("#texaspoker")) {
                    while (in.ready()) {
                        str = in.readLine();
                        if (str.startsWith("#")) {
                            break;
                        }
                        st = new StringTokenizer(str);
                        biggestpot = Integer.parseInt(st.nextToken());
                        house.set("biggestpot", biggestpot);
                        players = Integer.parseInt(st.nextToken());
                        for (int ctr = 0; ctr < players; ctr++) {
                            house.addDonor(new PokerPlayer(st.nextToken(), ""));
                        }
                        winners = Integer.parseInt(st.nextToken());
                        for (int ctr = 0; ctr < winners; ctr++) {
                            house.addWinner(new PokerPlayer(st.nextToken(), ""));
                        }
                    }
                    break;
                }
            }
            in.close();
        } catch (IOException e) {
            bot.log("housestats.txt not found! Creating new housestats.txt...");
            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("housestats.txt")));
                out.close();
            } catch (IOException f) {
                bot.log("Error creating housestats.txt!");
            }
        }
    }
    public void saveHouseStats() {
        boolean found = false;
        int index = 0;
        ArrayList<String> lines = new ArrayList<String>();
        try {
            BufferedReader in = new BufferedReader(new FileReader("housestats.txt"));
            String str;
            while (in.ready()) {
                //Add all lines until we find texaspoker lines
                str = in.readLine();
                lines.add(str);
                if (str.startsWith("#texaspoker")) {
                    found = true;
                    /* Store the index where texaspoker stats go so they can be 
                     * overwritten. */
                    index = lines.size();
                    //Skip existing texaspoker lines but add all the rest
                    while (in.ready()) {
                        str = in.readLine();
                        if (str.startsWith("#")) {
                            lines.add(str);
                            break;
                        }
                    }
                }
            }
            in.close();
        } catch (IOException e) {
            /* housestats.txt is not found */
            bot.log("Error reading housestats.txt!");
        }
        if (!found) {
            lines.add("#texaspoker");
            index = lines.size();
        }
        lines.add(index, house.get("biggestpot") + " " + house.getNumDonors() + " " + house.getDonorsString() + " " + house.getNumWinners() + " " + house.getWinnersString());
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("housestats.txt")));
            for (int ctr = 0; ctr < lines.size(); ctr++) {
                out.println(lines.get(ctr));
            }
            out.close();
        } catch (IOException e) {
            bot.log("Error writing to housestats.txt!");
        }
    }
    
    /* Card management methods for Texas Hold'em Poker */
    /**
     * Deals cards to the community hand.
     */
    public void dealCommunity(){
        if (get("stage") == 1) {
            for (int ctr = 1; ctr <= 3; ctr++){
                dealCard(community);
            }
        } else {
            dealCard(community);
        }
    }
    /**
     * Deals two cards to the specified player.
     * @param p the player to be dealt to
     */
    public void dealHand(PokerPlayer p) {
        dealCard(p.getHand());
        dealCard(p.getHand());
    }
    /**
     * Deals hands to everybody at the table.
     */
    public void dealTable() {
        PokerPlayer p;
        for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
            p = (PokerPlayer) getJoined(ctr);
            dealHand(p);
            infoPlayerHand(p, p.getHand());
        }
    }
    /**
     * Discards a player's hand into the discard pile.
     * @param p the player whose hand is to be discarded
     */
    public void discardPlayerHand(PokerPlayer p) {
        if (p.hasHand()) {
            deck.addToDiscard(p.getHand().getAllCards());
            p.resetHand();
        }
    }
    /**
     * Discards the community cards into the discard pile.
     */
    public void discardCommunity(){
        if (community.getSize() > 0){
            deck.addToDiscard(community.getAllCards());
            community.clear();
        }
    }
    /**
     * Merges the discards and shuffles the deck.
     */
    public void shuffleDeck() {
        deck.refillDeck();
        showShuffleDeck();
    }
    
    /* Texas Hold'em Poker gameplay methods */
    public int getNumberNotFolded(){
        PokerPlayer p;
        int numberNotFolded = 0;
        for (int ctr = 0; ctr < getNumberJoined(); ctr++){
            p = (PokerPlayer) getJoined(ctr);
            if (!p.has("fold")){
                numberNotFolded++;
            }
        }
        return numberNotFolded;
    }
    public int getNumberCanBet(){
        PokerPlayer p;
        int numberCanBet = 0;
        for (int ctr = 0; ctr < getNumberJoined(); ctr++){
            p = (PokerPlayer) getJoined(ctr);
            if (!p.has("fold") && !p.has("allin")){
                numberCanBet++;
            }
        }
        return numberCanBet;
    }
    public int getNumberBettors() {
        PokerPlayer p;
        int numberBettors = 0;
        for (int ctr = 0; ctr < getNumberJoined(); ctr++){
            p = (PokerPlayer) getJoined(ctr);
            if (p.has("bet")){
                numberBettors++;
            }
        }
        return numberBettors;
    }
    /**
     * Processes a bet command.
     * @param amount the amount to bet
     */
    public void bet (int amount) {
        cancelIdleOutTask();
        PokerPlayer p = (PokerPlayer) currentPlayer;
        
        // A bet that's an all-in
        if (amount == p.get("cash")){
            if (amount > get("currentbet") || topBettor == null){
                if (amount - get("currentbet") > get("minraise")){
                    set("minraise", amount - get("currentbet"));
                }
                set("currentbet", amount);
                topBettor = p;
            }
            p.set("bet", amount);
            p.set("allin", 1);
            showMsg(getMsg("tp_allin"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
            continueRound();
        // A bet that's larger than a player's stack
        } else if (amount > p.get("cash")) {
            informPlayer(p.getNick(), getMsg("insufficient_funds"));
            setIdleOutTask();
        // A bet that's lower than the current bet
        } else if (amount < get("currentbet")) {
            informPlayer(p.getNick(), getMsg("tp_bet_too_low"), get("currentbet"));
            setIdleOutTask();
        // A bet that's equivalent to a call or check
        } else if (amount == get("currentbet")){
            if (topBettor == null){
                topBettor = p;
            }
            if (amount == 0 || p.get("bet") == amount){
                showMsg(getMsg("tp_check"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
            } else {
                p.set("bet", amount);
                showMsg(getMsg("tp_call"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
            }
            continueRound();
        // A bet that's lower than the minimum raise
        } else if (amount - get("currentbet") < get("minraise")){
            informPlayer(p.getNick(), getMsg("raise_too_low"), get("minraise"));
            setIdleOutTask();
        // A valid bet that's greater than the currentBet
        } else {
            p.set("bet", amount);
            topBettor = p;
            if (get("currentbet") == 0){
                showMsg(getMsg("tp_bet"), p.getNickStr(), p.get("bet"), p.get("cash") - p.get("bet"));
            } else {
                showMsg(getMsg("tp_raise"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
            }
            set("minraise", amount - get("currentbet"));
            set("currentbet", amount);
            continueRound();
        }
    }
    /**
     * Processes a check command.
     * Only allow a player to check when the currentBet is 0 or if the player
     * has already committed the required amount to pot.
     */
    public void check(){
        cancelIdleOutTask();
        PokerPlayer p = (PokerPlayer) currentPlayer;
        
        if (get("currentbet") == 0 || p.get("bet") == get("currentbet")){
            if (topBettor == null){
                topBettor = p;
            }
            showMsg(getMsg("tp_check"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
            continueRound();
        } else {
            informPlayer(p.getNick(), getMsg("no_checking"), get("currentbet"));
            setIdleOutTask();
        }
    }
    /**
     * Processes a call command.
     * A player's bet will be matched to the currentBet. If a player's stack
     * is less than the currentBet, the player will move all-in.
     */
    public void call(){
        cancelIdleOutTask();
        PokerPlayer p = (PokerPlayer) currentPlayer;
        int total = Math.min(p.get("cash"), get("currentbet"));
        
        if (topBettor == null){
            topBettor = p;
        }
        
        // A call that's an all-in to match the currentBet
        if (total == p.get("cash")){
            p.set("allin", 1);
            p.set("bet", total);
            showMsg(getMsg("tp_allin"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
        // A check
        } else if (total == 0 || p.get("bet") == total){
            showMsg(getMsg("tp_check"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
        // A call
        } else {
            p.set("bet", total);
            showMsg(getMsg("tp_call"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
        }
        continueRound();
    }
    /**
     * Process a fold command.
     * The folding player is removed from all pots.
     */
    public void fold(){
        cancelIdleOutTask();
        PokerPlayer p = (PokerPlayer) currentPlayer;
        p.set("fold", 1);
        showMsg(getMsg("tp_fold"), p.getNickStr(), p.get("cash")-p.get("bet"));

        //Remove this player from any existing pots
        if (currentPot != null && currentPot.hasPlayer(p)){
            currentPot.removePlayer(p);
        }
        for (int ctr = 0; ctr < pots.size(); ctr++){
            PokerPot cPot = pots.get(ctr);
            if (cPot.hasPlayer(p)){
                cPot.removePlayer(p);
            }
        }
        continueRound();
    }
    /**
     * Adds the bets during a round of betting to the pot.
     * If no pot exists, a new one is created. Sidepots are created as necessary.
     */
    public void addBetsToPot(){
        PokerPlayer p;
        int lowBet;
        while(get("currentbet") != 0){
            lowBet = get("currentbet");
            // Only add bets to a pot if more than one player has made a bet
            if (getNumberBettors() > 1) {
                // Create a new pot if currentPot is set to null
                if (currentPot == null){
                    currentPot = new PokerPot();
                    pots.add(currentPot);
                } else {
                    // Determine if anybody in the current pot has no more bet
                    // left to contribute but is still in the game. If so, 
                    // then a new pot will be required.
                    for (int ctr = 0; ctr < currentPot.getNumPlayers(); ctr++) {
                        p = currentPot.getPlayer(ctr);
                        if (!p.has("bet") && get("currentbet") != 0 && !p.has("fold") && currentPot.hasPlayer(p)) {
                            currentPot = new PokerPot();
                            pots.add(currentPot);
                            break;
                        }
                    }
                }

                // Determine the lowest non-zero bet
                for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
                    p = (PokerPlayer) getJoined(ctr);
                    if (p.get("bet") < lowBet && p.has("bet")){
                        lowBet = p.get("bet");
                    }
                }
                // Subtract lowBet from each player's (non-zero) bet and add to pot.
                for (int ctr = 0; ctr < getNumberJoined(); ctr++){
                    p = (PokerPlayer) getJoined(ctr);
                    if (p.has("bet")){
                        // Check if player has been added to donor list
                        if (!currentPot.hasDonor(p)) {
                            currentPot.addDonor(p);
                        }
                        // Ensure a non-folded player is included in this pot
                        if (!p.has("fold") && !currentPot.hasPlayer(p)){
                            currentPot.addPlayer(p);
                        }
                        // Transfer lowBet from the player to the pot
                        currentPot.addPot(lowBet);
                        p.add("cash", -1 * lowBet);
                        p.add("tpwinnings", -1 * lowBet);
                        p.add("bet", -1 * lowBet);
                        p.add("change", -1 * lowBet);
                    }
                }
                // Update currentbet
                set("currentbet", get("currentbet") - lowBet);
            
            // If only one player has any bet left then it should not be
            // contributed to the current pot and his bet and currentBet should
            // be reset.
            } else {
                for (int ctr = 0; ctr < getNumberJoined(); ctr++){
                    p = (PokerPlayer) getJoined(ctr);
                    if (p.get("bet") != 0){
                        p.clear("bet");
                        break;
                    }
                }
                set("currentbet", 0);
                break;
            }
        }
    }
    
    /* Channel message output methods for Texas Hold'em Poker*/
    @Override
    public void showGameStats() {
        int totalPlayers;
        totalPlayers = getTotalPlayers();
        if (totalPlayers == 1){
            bot.sendMessage(channel, formatNumber(totalPlayers)+" player has played " +	getGameNameStr()+". " + house);
        } else {
            bot.sendMessage(channel, formatNumber(totalPlayers)+" players have played " + getGameNameStr()+". " + house);
        }
    }
    public void showTablePlayers(){
        PokerPlayer p;
        String msg = formatBold(getNumberJoined()+"") + " players: ";
        String nickColor = "";
        for (int ctr = 0; ctr < getNumberJoined(); ctr++){
            p = (PokerPlayer) getJoined(ctr);
            // Give bold to remaining non-folded players
            if (!p.has("fold")){
                nickColor = Colors.BOLD;
            } else {
                nickColor = "";
            }
            msg += nickColor+p.getNick();

            // Give special players a label
            if (p == dealer || p == smallBlind || p == bigBlind){
                msg += "(";
                if (p == dealer){
                    msg += "D";
                }
                if (p == smallBlind){
                    msg += "S";
                } else if (p == bigBlind){
                    msg += "B";
                }
                msg += ")";
            }
            msg += nickColor;
            if (ctr != getNumberJoined()-1){
                msg += ", ";
            }
        }
        bot.sendMessage(channel, msg);
    }
    public void showCommunityCards(){
        PokerPlayer p;
        StringBuilder msg = new StringBuilder();
        // Append community cards to StringBuilder
        String str = formatHeader(" Community Cards: ") + " " + community.toString() + " ";
        msg.append(str);
        
        // Append existing pots to StringBuilder
        for (int ctr = 0; ctr < pots.size(); ctr++){
            str = Colors.YELLOW+",01Pot #"+(ctr+1)+": "+Colors.GREEN+",01$"+formatNumber(pots.get(ctr).getPot())+Colors.NORMAL+" ";
            msg.append(str);
        }
        
        // Append remaining non-folded players
        int notFolded = getNumberNotFolded();
        int count = 0;
        str = "(" + formatBold(notFolded) + " players: ";
        for (int ctr = 0; ctr < getNumberJoined(); ctr++){
            p = (PokerPlayer) getJoined(ctr);
            if (!p.has("fold")){
                str += p.getNick();
                if (count != notFolded-1){
                    str += ", ";
                }
                count++;
            }
        }
        str += ")";
        msg.append(str);
        
        bot.sendMessage(channel, msg.toString());
    }
    public void showPlayerResult(PokerPlayer p){
        bot.sendMessage(channel, p.getNickStr() + " (" + p.getHand() + ")" + ": "+ p.getPokerHand().getName()+", " + p.getPokerHand() );
    }
    public void showResults(){
        ArrayList<PokerPlayer> players;
        PokerPlayer p;
        int winners;
        // Show introduction to end results
        bot.sendMessage(channel, formatHeader(" Results: "));
        players = pots.get(0).getPlayers();
        Collections.sort(players);
        Collections.reverse(players);
        // Show each remaining player's hand
        if (pots.get(0).getNumPlayers() > 1){
            for (int ctr = 0; ctr < players.size(); ctr++){
                p = players.get(ctr);
                showPlayerResult(p);
            }
        }
        // Find the winner(s) from each pot
        for (int ctr = 0; ctr < pots.size(); ctr++){
            winners = 1;
            currentPot = pots.get(ctr);
            players = currentPot.getPlayers();
            Collections.sort(players);
            Collections.reverse(players);
            // Determine number of winners
            for (int ctr2=1; ctr2 < currentPot.getNumPlayers(); ctr2++){
                if (players.get(0).compareTo(players.get(ctr2)) == 0){
                    winners++;
                }
            }
            
            // Output winners
            for (int ctr2=0; ctr2<winners; ctr2++){
                p = players.get(ctr2);
                p.add("cash", currentPot.getPot()/winners);
                p.add("tpwinnings", currentPot.getPot()/winners);
                p.add("change", currentPot.getPot()/winners);
                bot.sendMessage(channel, Colors.YELLOW+",01 Pot #" + (ctr+1) + ": " + Colors.NORMAL + " " + 
                    p.getNickStr() + " wins $" + formatNumber(currentPot.getPot()/winners) + 
                    ". Stack: $" + formatNumber(p.get("cash"))+ " (" + getPlayerListString(currentPot.getPlayers()) + ")");
            }
            
            // Check if it's the biggest pot
            if (house.get("biggestpot") < currentPot.getPot()){
                house.set("biggestpot", currentPot.getPot());
                house.clearDonors();
                house.clearWinners();
                // Store the list of donors
                for (int ctr2 = 0; ctr2 < currentPot.getNumDonors(); ctr2++){
                    house.addDonor(new PokerPlayer(currentPot.getDonor(ctr2).getNick(), ""));
                }
                // Store the list of winners
                for (int ctr2 = 0; ctr2 < winners; ctr2++){
                    house.addWinner(new PokerPlayer(currentPot.getPlayer(ctr2).getNick(), ""));
                }
                saveHouseStats();
            }
        }
    }
    public void showShuffleDeck() {
        bot.sendMessage(channel, "The deck has been shuffled.");
    }
    
    /* Private messages to players */
    /**
     * Informs a player of his hand.
     * The information is sent by notice if simple is true and by message if
     * simple is false.
     * 
     * @param p the player
     * @param h the hand
     */
    public void infoPlayerHand(PokerPlayer p, Hand h) {
        if (p.isSimple()) {
            bot.sendNotice(p.getNick(), "Your hand is " + h + ".");
        } else {
            bot.sendMessage(p.getNick(), "Your hand is " + h + ".");
        }
    }
    
    @Override
    public String getGameRulesStr() {
        return "This is no limit Texas Hold'em Poker. Blind bets are set at $" + 
            formatNumber(get("minbet")/2) + "/$" + formatNumber(get("minbet")) + 
            " or your stack, whichever is lower.";
    }
    @Override
    public String getGameCommandStr() {
        return "go, join, quit, bet, check, call, raise, fold, community, turn, " +
           "hand, cash, netcash, bank, transfer, deposit, withdraw, " + 
           "bankrupts, winnings, winrate, rounds, player, players, " +
           "waitlist, blacklist, rank, top, simple, stats, game, ghelp, " +
           "grules, gcommands";
    }
}
