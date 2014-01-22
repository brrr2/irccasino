/*
    Copyright (C) 2013-2014 Yizhe Shen <brrr@live.ca>

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

package irccasino.texaspoker;

import irccasino.cardgame.CardDeck;
import irccasino.GameManager;
import irccasino.StatFileLine;
import irccasino.cardgame.CardGame;
import irccasino.cardgame.Hand;
import irccasino.cardgame.Player;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import org.pircbotx.*;

public class TexasPoker extends CardGame{
    
    protected ArrayList<PokerPot> pots;
    protected PokerPot currentPot;
    protected PokerPlayer dealer, smallBlind, bigBlind, topBettor;
    protected Hand community;
    protected HouseStat house;
    // In-game properties
    protected int stage, currentBet, minRaise;
    
    public TexasPoker() {
        super();
    }
    
    /**
     * The default constructor for TexasPoker, subclass of CardGame.
     * This constructor loads the default INI file.
     * 
     * @param parent The bot that uses an instance of this class
     * @param commChar The command char
     * @param gameChannel The IRC channel in which the game is to be run.
     */
    public TexasPoker(GameManager parent, char commChar, Channel gameChannel){
        this(parent, commChar, gameChannel, "texaspoker.ini");
    }
    
    /**
     * Allows a custom INI file to be loaded.
     * 
     * @param parent The bot that uses an instance of this class
     * @param commChar The command char
     * @param gameChannel The IRC channel in which the game is to be run.
     * @param customINI the file path to a custom INI file
     */
    public TexasPoker(GameManager parent, char commChar, Channel gameChannel, String customINI) {
        super(parent, commChar, gameChannel);
        name = "texaspoker";
        iniFile = customINI;
        helpFile = "texaspoker.help";
        strFile = "strlib.txt";
        loadStrLib(strFile);
        loadHelp(helpFile);
        house = new HouseStat();
        loadGameStats();
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
        String host = user.getHostmask();
        
        /* Check if it's a common command */
        super.processCommand(user, command, params);
        
        /* Parsing commands from the channel */
        if (command.equals("start") || command.equals("go")) {
            if (isStartAllowed(nick)){
                if (params.length > 0){
                    try {
                        startCount = Math.min(get("autostarts") - 1, Integer.parseInt(params[0]) - 1);
                    } catch (NumberFormatException e) {
                        // Do nothing and proceed
                    }
                }
                inProgress = true;
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
                        bet(Integer.parseInt(params[0]) + currentBet);
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
            } else if (!inProgress) {
                informPlayer(nick, getMsg("no_start"));
            } else if (stage == 0){
                informPlayer(nick, getMsg("no_community"));
            } else {
                showCommunityCards();
            }
        } else if (command.equals("hand")) {
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else if (!inProgress) {
                informPlayer(nick, getMsg("no_start"));
            } else {
                PokerPlayer p = (PokerPlayer) findJoined(nick);
                informPlayer(p.getNick(), getMsg("tp_hand"), p.getHand());
            }
        } else if (command.equals("turn")) {
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else if (!inProgress) {
                informPlayer(nick, getMsg("no_start"));
            } else {
                showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), currentPlayer.get("cash")-currentPlayer.get("bet"), 
                        currentPlayer.get("bet"), currentBet, getCashInPlay());
            }
        } else if (command.equals("players")) {
            if (inProgress){
                showTablePlayers();
            } else {
                showMsg(getMsg("players"), getPlayerListString(joined));
            }
        /* Op commands */
        } else if (command.equals("fstart") || command.equals("fgo")){
            if (isForceStartAllowed(user,nick)){
                inProgress = true;
                showStartRound();
                setStartRoundTask();
            }
        } else if (command.equals("fstop")){
            // Use only as last resort. Data will be lost.
            if (isForceStopAllowed(user,nick)){
                PokerPlayer p;
                cancelIdleOutTask();
                for (int ctr = 0; ctr < joined.size(); ctr++) {
                    p = (PokerPlayer) joined.get(ctr);
                    resetPlayer(p);
                }
                resetGame();
                showMsg(getMsg("end_round"), getGameNameStr(), commandChar);
                inProgress = false;
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
                        bet(Integer.parseInt(params[0]) + currentBet);
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
                cmdMap.clear();
                opCmdMap.clear();
                aliasMap.clear();
                msgMap.clear();
                loadStrLib(strFile);
                loadHelp(helpFile);
                showMsg(getMsg("reload"));
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
                            showMsg("Player "+p.getNickStr()+": "+p.getHand().toString());
                        }
                        // Generate community cards
                        Hand comm = new Hand();
                        for (int ctr=0; ctr<5; ctr++){
                            dealCard(comm);
                        }
                        showMsg(formatHeader(" Community: ") + " " + comm.toString());
                        // Propagate poker hands
                        for (int ctr=0; ctr < number; ctr++){
                            p = peeps.get(ctr);
                            ph = p.getPokerHand();
                            ph.addAll(p.getHand());
                            ph.addAll(comm);
                            Collections.sort(ph);
                            Collections.reverse(ph);
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
                            showMsg("Player "+p.getNickStr()+
                                    " ("+p.getHand()+"), "+ph.getName()+": " + ph+" (WINNER)");
                        }
                        for (int ctr=winners; ctr < peeps.size(); ctr++){
                            p = peeps.get(ctr);
                            ph = p.getPokerHand();
                            showMsg("Player "+p.getNickStr()+
                                    " ("+p.getHand()+"), "+ph.getName()+": " + ph);
                        }
                        // Discard and shuffle
                        for (int ctr=0; ctr < number; ctr++){
                            resetPlayer(peeps.get(ctr));
                        }
                        deck.addToDiscard(comm);
                        comm.clear();
                        deck.refillDeck();
                        showMsg(getMsg("separator"));
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
                        showMsg(h.toString(0, h.size()));
                        Collections.sort(h);
                        Collections.reverse(h);
                        h.getValue();
                        showMsg(h.getName()+": " + h);
                        deck.addToDiscard(h);
                        h.clear();
                        deck.refillDeck();
                        showMsg(getMsg("separator"));
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
                
            }
        } else if (command.equals("test3")){
            // 3. Tests the percentage calculator for 2-5 players
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        int number = Integer.parseInt(params[0]);
                        if (number > 5 || number < 2){
                            throw new NumberFormatException();
                        }
                        ArrayList<PokerPlayer> peeps = new ArrayList<PokerPlayer>();
                        PokerPlayer p;
                        Hand comm = new Hand();
                        PokerSimulator sim;

                        // Generate players and deal cards
                        for (int ctr = 0; ctr < number; ctr++) {
                            p = new PokerPlayer(ctr + "", ctr + "");
                            peeps.add(p);
                            dealHand(p);
                            showMsg("Player "+p.getNickStr()+": "+p.getHand().toString());
                        }

                        // Calculate percentages
                        sim = new PokerSimulator(peeps, comm);
                        showMsg(sim.toString());

                        // Deal flop
                        for (int ctr = 0; ctr < 3; ctr++){
                            dealCard(comm);
                        }
                        showMsg(formatHeader(" Community: ") + " " + comm.toString());

                        // Recalculate percentages
                        sim = new PokerSimulator(peeps, comm);
                        showMsg(sim.toString());

                        // Deal turn
                        dealCard(comm);
                        showMsg(formatHeader(" Community: ") + " " + comm.toString());

                        // Recalculate percentages
                        sim = new PokerSimulator(peeps, comm);
                        showMsg(sim.toString());

                        // Deal river
                        dealCard(comm);
                        showMsg(formatHeader(" Community: ") + " " + comm.toString());
                        
                        // Recalculate percentages
                        sim = new PokerSimulator(peeps, comm);
                        showMsg(sim.toString());

                        // Discard and shuffle
                        for (int ctr=0; ctr < number; ctr++){
                            resetPlayer(peeps.get(ctr));
                        }
                        deck.addToDiscard(comm);
                        comm.clear();
                        deck.refillDeck();
                        showMsg(getMsg("separator"));
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
    public void addPlayer(String nick, String host) {
        addPlayer(new PokerPlayer(nick, host));
    }
    
    @Override
    public void addWaitlistPlayer(String nick, String host) {
        Player p = new PokerPlayer(nick, host);
        waitlist.add(p);
        informPlayer(p.getNick(), getMsg("join_waitlist"));
    }
    
    @Override
    public void startRound() {
        if (joined.size() < 2){
            startCount = 0;
            endRound();
        } else {
            setButton();
            showTablePlayers();
            dealTable();
            setBlindBets();
            currentPlayer = getPlayerAfter(bigBlind);
            showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), currentPlayer.get("cash")-currentPlayer.get("bet"), 
                        currentPlayer.get("bet"), currentBet, getCashInPlay());
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
            minRaise = get("minbet");
            // Add bets from this round of betting to the pot
            addBetsToPot();
            stage++;
            
            // If all community cards have been dealt, move to end of round
            if (stage == 4){
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
                    ArrayList<PokerPlayer> players;
                    PokerSimulator sim;
                    players = pots.get(0).getPlayers();
                    sim = new PokerSimulator(players, community);
                    String showdownStr = formatHeader(" Showdown: ") + " ";
                    for (int ctr = 0; ctr < players.size(); ctr++) {
                        p = players.get(ctr);
                        showdownStr += p.getNickStr() + " (" + p.getHand() + "||" + formatBold(sim.getWinPct(p) + "/" + sim.getTiePct(p) + "%%") + ")";
                        if (ctr < players.size()-1){
                            showdownStr += ", ";
                        }
                    }
                    showMsg(showdownStr);
                   
                   // Add a delay for dramatic effect
                   try { Thread.sleep(get("showdown") * 1000); } catch (InterruptedException e){}
                }
                
                // Burn a card before flop, turn and river
                burnCard();
                dealCommunity();
                
                /* Only show dealt community cards when there are 
                 * more than 1 non-folded player remaining. Also
                 * show community if stage = 3 and "revealcommunity"
                 * is set to true. */
                if (getNumberNotFolded() > 1 || 
                        stage == 3 && settings.get("revealcommunity") == 1){
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
            showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), currentPlayer.get("cash")-currentPlayer.get("bet"),
                        currentPlayer.get("bet"), currentBet, getCashInPlay());
            setIdleOutTask();
        }
    }
    
    @Override
    public void endRound() {
        PokerPlayer p;
        ArrayList<Player> roundPlayers = new ArrayList<Player>(joined);
        
        roundEnded = true;

        // Check if anybody left during post-start waiting period
        if (joined.size() > 1 && pots.size() > 0) {
            // Give all non-folded players the community cards
            for (int ctr = 0; ctr < joined.size(); ctr++){
                p = (PokerPlayer) joined.get(ctr);
                if (!p.has("fold")){
                    p.getPokerHand().addAll(p.getHand());
                    p.getPokerHand().addAll(community);
                    Collections.sort(p.getPokerHand());
                    Collections.reverse(p.getPokerHand());
                }
            }

            // Determine the winners of each pot
            showResults();

            // Show the stack changes
            showStackChange();

            // Show updated player stacks sorted in descending order
            showStacks();
            
            /* Clean-up tasks
             * 1. Increment the number of rounds played for player
             * 2. Remove players who have gone bankrupt and set respawn timers
             * 3. Remove players who have quit mid-round
             * 4. Save player data
             * 5. Reset the player
             */
            for (int ctr = 0; ctr < joined.size(); ctr++){
                p = (PokerPlayer) joined.get(ctr);
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
                
                // Reset player
                resetPlayer(p);
            }
        } else {
            showMsg(getMsg("no_players"));
        }
        
        resetGame(); 
        showMsg(getMsg("end_round"), getGameNameStr(), commandChar);
        mergeWaitlist();
        
        // Check if auto-starts remaining
        if (startCount > 0){
            startCount--;
            if (!inProgress){
                if (joined.size() > 1) {
                    inProgress = true;
                    showStartRound();
                    setStartRoundTask();
                } else {
                    startCount = 0;
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
        cmdMap.clear();
        opCmdMap.clear();
        aliasMap.clear();
        msgMap.clear();
        settings.clear();
    }
    
    @Override
    public void resetGame() {
        stage = 0;
        currentBet = 0;
        minRaise = 0;
        inProgress = false;
        roundEnded = false;
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
            if (inProgress) {
                /* If still in the post-start waiting phase, then currentPlayer has
                 * not been set yet. */
                if (currentPlayer == null){
                    removeJoined(p);
                // Check if it is already in the endRound stage
                } else if (roundEnded){
                    p.set("quit", 1);
                    informPlayer(p.getNick(), getMsg("remove_end_round"));
                // Force the player to fold if it is his turn
                } else if (p == currentPlayer){
                    p.set("quit", 1);
                    informPlayer(p.getNick(), getMsg("remove_end_round"));
                    fold();
                } else {
                    p.set("quit", 1);
                    informPlayer(p.getNick(), getMsg("remove_end_round"));
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
    
    /**
     * Returns next player after the specified player.
     * @param p the specified player
     * @return the next player
     */
    protected Player getPlayerAfter(Player p){
        return joined.get((joined.indexOf(p) + 1) % joined.size());
    }
    
    /**
     * Resets the specified player.
     * @param p the player to reset
     */
    protected void resetPlayer(PokerPlayer p) {
        discardPlayerHand(p);
        p.clear("fold");
        p.clear("quit");
        p.clear("allin");
        p.clear("change");
    }
    
    /**
     * Assigns players to the dealer, small blind and big blind roles.
     */
    protected void setButton(){
        if (dealer == null){
            dealer = (PokerPlayer) joined.get(0);
        } else {
            dealer = (PokerPlayer) getPlayerAfter(dealer);
        }
        if (joined.size() == 2){
            smallBlind = dealer;
        } else {
            smallBlind = (PokerPlayer) getPlayerAfter(dealer);
        }
        bigBlind = (PokerPlayer) getPlayerAfter(smallBlind);
    }
    
    /**
     * Sets the bets for the small and big blinds.
     */
    protected void setBlindBets(){
        // Set the small blind to minimum raise or the player's cash, 
        // whichever is less.
        smallBlind.set("bet", Math.min(get("minbet")/2, smallBlind.get("cash")));
        // Set the big blind to minimum raise + small blind or the player's 
        // cash, whichever is less.
        bigBlind.set("bet", Math.min(get("minbet"), bigBlind.get("cash")));
        // Set the current bet to the bigger of the two blinds.
        currentBet = Math.max(smallBlind.get("bet"), bigBlind.get("bet"));
        minRaise = get("minbet");
    }
    
    /* Game command logic checking methods */
    public boolean isStartAllowed(String nick){
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (inProgress) {
            informPlayer(nick, getMsg("round_started"));
        } else if (joined.size() < 2) {
            showMsg(getMsg("no_players"));
        } else {
            return true;
        }
        return false;
    }
    public boolean isPlayerTurn(String nick){
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else if (!inProgress) {
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
        } else if (inProgress) {
            informPlayer(nick, getMsg("round_started"));
        } else if (joined.size() < 2) {
            showMsg(getMsg("no_players"));
        } else {
            return true;
        }
        return false;
    }
    public boolean isForceStopAllowed(User user, String nick){
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!inProgress) {
            informPlayer(nick, getMsg("no_start"));
        } else {
            return true;
        }
        return false;
    }
    public boolean isForcePlayAllowed(User user, String nick){
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!inProgress) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("no_force_play"));
        } else {
            return true;
        }
        return false;
    }
    
    /* Game settings management */
    @Override
    protected void initialize(){
        super.initialize();
        // Do not use set()
        // Ini file settings
        settings.put("cash", 1000);
        settings.put("idle", 60);
        settings.put("idlewarning", 45);
        settings.put("respawn", 600);
        settings.put("maxplayers", 22);
        settings.put("minbet", 10);
        settings.put("autostarts", 10);
        settings.put("startwait", 5);
        settings.put("showdown", 10);
        settings.put("revealcommunity", 0);
        // In-game properties
        stage = 0;
        currentBet = 0;
        minRaise = 0;
    }
    
    @Override
    protected void saveIniFile() {
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(iniFile)));
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
            out.println("#The wait time in seconds after the start command is given");
            out.println("startwait=" + get("startwait"));
            out.println("#The wait time in seconds in between reveals during a showdown");
            out.println("showdown=" + get("showdown"));
            out.println("#Whether or not to reveal community when not required");
            out.println("revealcommunity=" + get("revealcommunity"));
            out.close();
        } catch (IOException e) {
            manager.log("Error creating " + iniFile + "!");
        }
    }
    
    /* House stats management */
    @Override
    public void loadGameStats() {
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
            manager.log("housestats.txt not found! Creating new housestats.txt...");
            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("housestats.txt")));
                out.close();
            } catch (IOException f) {
                manager.log("Error creating housestats.txt!");
            }
        }
    }
    
    @Override
    public void saveGameStats() {
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
            manager.log("Error reading housestats.txt!");
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
            manager.log("Error writing to housestats.txt!");
        }
    }
    
    /* Card management methods for Texas Hold'em Poker */
    
    /**
     * Deals cards to the community hand.
     */
    protected void dealCommunity(){
        if (stage == 1) {
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
    protected void dealHand(PokerPlayer p) {
        dealCard(p.getHand());
        dealCard(p.getHand());
    }
    
    /**
     * Deals hands to everybody at the table.
     */
    protected void dealTable() {
        PokerPlayer p;
        for (int ctr = 0; ctr < joined.size(); ctr++) {
            p = (PokerPlayer) joined.get(ctr);
            dealHand(p);
            informPlayer(p.getNick(), getMsg("tp_hand"), p.getHand());
        }
    }
    
    /**
     * Discards a player's hand into the discard pile.
     * @param p the player whose hand is to be discarded
     */
    protected void discardPlayerHand(PokerPlayer p) {
        if (p.hasHand()) {
            deck.addToDiscard(p.getHand());
            p.resetHand();
        }
    }
    
    /**
     * Discards the community cards into the discard pile.
     */
    protected void discardCommunity(){
        if (community.size() > 0){
            deck.addToDiscard(community);
            community.clear();
        }
    }
    
    /**
     * Merges the discards and shuffles the deck.
     */
    protected void shuffleDeck() {
        deck.refillDeck();
        showMsg(getMsg("tp_shuffle_deck"));
    }
    
    /* Texas Hold'em Poker gameplay methods */
    
    /**
     * Processes a bet command.
     * @param amount the amount to bet
     */
    public void bet (int amount) {
        cancelIdleOutTask();
        PokerPlayer p = (PokerPlayer) currentPlayer;
        
        // A bet that's an all-in
        if (amount == p.get("cash")){
            if (amount > currentBet || topBettor == null){
                if (amount - currentBet > minRaise){
                    minRaise = amount - currentBet;
                }
                currentBet = amount;
                topBettor = p;
            }
            p.set("bet", amount);
            p.set("allin", 1);
            showMsg(getMsg("tp_allin"), p.getNickStr(false), p.get("bet"), p.get("cash")-p.get("bet"));
            continueRound();
        // A bet that's larger than a player's stack
        } else if (amount > p.get("cash")) {
            informPlayer(p.getNick(), getMsg("insufficient_funds"));
            setIdleOutTask();
        // A bet that's lower than the current bet
        } else if (amount < currentBet) {
            informPlayer(p.getNick(), getMsg("tp_bet_too_low"), currentBet);
            setIdleOutTask();
        // A bet that's equivalent to a call or check
        } else if (amount == currentBet){
            if (topBettor == null){
                topBettor = p;
            }
            if (amount == 0 || p.get("bet") == amount){
                showMsg(getMsg("tp_check"), p.getNickStr(false), p.get("bet"), p.get("cash")-p.get("bet"));
            } else {
                p.set("bet", amount);
                showMsg(getMsg("tp_call"), p.getNickStr(false), p.get("bet"), p.get("cash")-p.get("bet"));
            }
            continueRound();
        // A bet that's lower than the minimum raise
        } else if (amount - currentBet < minRaise){
            informPlayer(p.getNick(), getMsg("raise_too_low"), minRaise);
            setIdleOutTask();
        // A valid bet that's greater than the currentBet
        } else {
            p.set("bet", amount);
            topBettor = p;
            if (currentBet == 0){
                showMsg(getMsg("tp_bet"), p.getNickStr(false), p.get("bet"), p.get("cash") - p.get("bet"));
            } else {
                showMsg(getMsg("tp_raise"), p.getNickStr(false), p.get("bet"), p.get("cash")-p.get("bet"));
            }
            minRaise = amount - currentBet;
            currentBet = amount;
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
        
        if (currentBet == 0 || p.get("bet") == currentBet){
            if (topBettor == null){
                topBettor = p;
            }
            showMsg(getMsg("tp_check"), p.getNickStr(false), p.get("bet"), p.get("cash")-p.get("bet"));
            continueRound();
        } else {
            informPlayer(p.getNick(), getMsg("no_checking"), currentBet);
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
        int total = Math.min(p.get("cash"), currentBet);
        
        if (topBettor == null){
            topBettor = p;
        }
        
        // A call that's an all-in to match the currentBet
        if (total == p.get("cash")){
            p.set("allin", 1);
            p.set("bet", total);
            showMsg(getMsg("tp_allin"), p.getNickStr(false), p.get("bet"), p.get("cash")-p.get("bet"));
        // A check
        } else if (total == 0 || p.get("bet") == total){
            showMsg(getMsg("tp_check"), p.getNickStr(false), p.get("bet"), p.get("cash")-p.get("bet"));
        // A call
        } else {
            p.set("bet", total);
            showMsg(getMsg("tp_call"), p.getNickStr(false), p.get("bet"), p.get("cash")-p.get("bet"));
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
        showMsg(getMsg("tp_fold"), p.getNickStr(false), p.get("cash")-p.get("bet"));

        //Remove this player from any existing pots
        if (currentPot != null && currentPot.hasPlayer(p)){
            currentPot.removePlayer(p);
        }
        for (PokerPot pot : pots) {
            if (pot.hasPlayer(p)){
                pot.removePlayer(p);
            }
        }
        continueRound();
    }
    
    /* Behind the scenes methods */
    
    /**
     * Determines the number of players who have not folded.
     * @return the number of non-folded players
     */
    protected int getNumberNotFolded(){
        PokerPlayer p;
        int numberNotFolded = 0;
        for (int ctr = 0; ctr < joined.size(); ctr++){
            p = (PokerPlayer) joined.get(ctr);
            if (!p.has("fold")){
                numberNotFolded++;
            }
        }
        return numberNotFolded;
    }
    
    /**
     * Determines the number players who can still make a bet.
     * @return the number of players who can bet
     */
    protected int getNumberCanBet(){
        PokerPlayer p;
        int numberCanBet = 0;
        for (int ctr = 0; ctr < joined.size(); ctr++){
            p = (PokerPlayer) joined.get(ctr);
            if (!p.has("fold") && !p.has("allin")){
                numberCanBet++;
            }
        }
        return numberCanBet;
    }
    
    /**
     * Determines the number of players who have made a bet in a round of 
     * betting.
     * @return the number of bettors
     */
    protected int getNumberBettors() {
        PokerPlayer p;
        int numberBettors = 0;
        for (int ctr = 0; ctr < joined.size(); ctr++){
            p = (PokerPlayer) joined.get(ctr);
            if (p.has("bet")){
                numberBettors++;
            }
        }
        return numberBettors;
    }
    
    /**
     * Determines total amount committed by all players.
     * @return the total running amount 
     */
    protected int getCashInPlay() {
        int total = 0;
        PokerPlayer p;
        
        // Add in the processed pots
        for (PokerPot pp : pots) {
            total += pp.getTotal();
        }
        
        // Add in the amounts currently being betted
        for (int ctr = 0; ctr < joined.size(); ctr++) {
            p = (PokerPlayer) joined.get(ctr);
            total += p.get("bet");
        }
        
        return total;
    }
    
    /**
     * Adds the bets during a round of betting to the pot.
     * If no pot exists, a new one is created. Sidepots are created as necessary.
     */
    protected void addBetsToPot(){
        PokerPlayer p;
        int lowBet;
        while(currentBet != 0){
            lowBet = currentBet;
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
                        if (!p.has("bet") && currentBet != 0 && !p.has("fold") && currentPot.hasPlayer(p)) {
                            currentPot = new PokerPot();
                            pots.add(currentPot);
                            break;
                        }
                    }
                }

                // Determine the lowest non-zero bet
                for (int ctr = 0; ctr < joined.size(); ctr++) {
                    p = (PokerPlayer) joined.get(ctr);
                    if (p.get("bet") < lowBet && p.has("bet")){
                        lowBet = p.get("bet");
                    }
                }
                // Subtract lowBet from each player's (non-zero) bet and add to pot.
                for (int ctr = 0; ctr < joined.size(); ctr++){
                    p = (PokerPlayer) joined.get(ctr);
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
                        currentPot.add(lowBet);
                        p.add("cash", -1 * lowBet);
                        p.add("tpwinnings", -1 * lowBet);
                        p.add("bet", -1 * lowBet);
                        p.add("change", -1 * lowBet);
                    }
                }
                // Update currentbet
                currentBet -= lowBet;
            
            // If only one player has any bet left then it should not be
            // contributed to the current pot and his bet and currentBet should
            // be reset.
            } else {
                for (int ctr = 0; ctr < joined.size(); ctr++){
                    p = (PokerPlayer) joined.get(ctr);
                    if (p.get("bet") != 0){
                        p.clear("bet");
                        break;
                    }
                }
                currentBet = 0;
                break;
            }
        }
    }
    
    @Override
    public int getTotalPlayers(){
        try {
            ArrayList<StatFileLine> statList = new ArrayList<StatFileLine>();
            loadPlayerFile(statList);
            int total = 0;
            
            for (StatFileLine statLine : statList) {
                if (statLine.has("tprounds")){
                    total++;
                }
            }
            return total;
        } catch (IOException e){
            manager.log("Error reading players.txt!");
            return 0;
        }
    }    
    
    /* Channel message output methods for Texas Hold'em Poker*/
    
    /**
     * Displays the players who are involved in a round. Players who have not
     * folded are displayed in bold. Designations for small blind, big blind,
     * and dealer are also shown.
     */
    public void showTablePlayers(){
        PokerPlayer p;
        String msg = formatBold(joined.size()) + " players: ";
        String nickColor;
        for (int ctr = 0; ctr < joined.size(); ctr++){
            p = (PokerPlayer) joined.get(ctr);
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
            if (ctr != joined.size() - 1){
                msg += ", ";
            }
        }
        showMsg(msg);
    }
    
    /**
     * Displays the community cards along with existing pots.
     */
    public void showCommunityCards(){
        PokerPlayer p;
        StringBuilder msg = new StringBuilder();
        // Append community cards to StringBuilder
        String str = formatHeader(" Community: ") + " " + community.toString() + " ";
        msg.append(str);
        
        // Append existing pots to StringBuilder
        for (int ctr = 0; ctr < pots.size(); ctr++){
            str = Colors.YELLOW+",01 Pot #"+(ctr+1)+": "+Colors.GREEN+",01$"+formatNumber(pots.get(ctr).getTotal())+" "+Colors.NORMAL+" ";
            msg.append(str);
        }
        
        // Append remaining non-folded players
        int notFolded = getNumberNotFolded();
        int count = 0;
        str = "(" + formatBold(notFolded) + " players: ";
        for (int ctr = 0; ctr < joined.size(); ctr++){
            p = (PokerPlayer) joined.get(ctr);
            if (!p.has("fold")){
                str += p.getNick(false);
                if (count != notFolded-1){
                    str += ", ";
                }
                count++;
            }
        }
        str += ")";
        msg.append(str);
        
        showMsg(msg.toString());
    }

    /**
     * Displays the results of a round.
     */
    public void showResults(){
        ArrayList<PokerPlayer> players;
        PokerPlayer p;
        int winners;
        // Show introduction to end results
        showMsg(formatHeader(" Results: "));
        players = pots.get(0).getPlayers();
        Collections.sort(players);
        Collections.reverse(players);
        // Show each remaining player's hand
        if (pots.get(0).getNumPlayers() > 1){
            for (int ctr = 0; ctr < players.size(); ctr++){
                p = players.get(ctr);
                showMsg(getMsg("tp_player_result"), p.getNickStr(false), p.getHand(), p.getPokerHand().getName(), p.getPokerHand());
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
                p.add("cash", currentPot.getTotal()/winners);
                p.add("tpwinnings", currentPot.getTotal()/winners);
                p.add("change", currentPot.getTotal()/winners);
                showMsg(Colors.YELLOW+",01 Pot #" + (ctr+1) + ": " + Colors.NORMAL + " " + 
                    p.getNickStr() + " wins $" + formatNumber(currentPot.getTotal()/winners) + 
                    ". (" + getPlayerListString(currentPot.getPlayers()) + ")");
            }
            
            // Check if it's the biggest pot
            if (house.get("biggestpot") < currentPot.getTotal()){
                house.set("biggestpot", currentPot.getTotal());
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
                saveGameStats();
            }
        }
    }
    
    @Override
    public void showPlayerWinnings(String nick){
        int winnings = getPlayerStat(nick, "tpwinnings");
        if (winnings != Integer.MIN_VALUE) {
            showMsg(getMsg("player_winnings"), formatNoPing(nick), winnings, getGameNameStr());
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }
    
    @Override
    public void showPlayerWinRate(String nick){
        double winnings = (double) getPlayerStat(nick, "tpwinnings");
        double rounds = (double) getPlayerStat(nick, "tprounds");
        
        if (rounds != Integer.MIN_VALUE) {
            if (rounds == 0){
                showMsg(getMsg("player_no_rounds"), formatNoPing(nick), getGameNameStr());
            } else {
                showMsg(getMsg("player_winrate"), formatNoPing(nick), winnings/rounds, getGameNameStr());
            }    
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }
    
    @Override
    public void showPlayerRounds(String nick){
        int rounds = getPlayerStat(nick, "tprounds");
        if (rounds != Integer.MIN_VALUE) {
            if (rounds == 0){
                showMsg(getMsg("player_no_rounds"), formatNoPing(nick), getGameNameStr());
            } else {
                showMsg(getMsg("player_rounds"), formatNoPing(nick), rounds, getGameNameStr());
            }  
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }
    
    @Override
    public void showPlayerAllStats(String nick){
        int cash = getPlayerStat(nick, "cash");
        int bank = getPlayerStat(nick, "bank");
        int net = getPlayerStat(nick, "netcash");
        int bankrupts = getPlayerStat(nick, "bankrupts");
        int winnings = getPlayerStat(nick, "tpwinnings");
        int rounds = getPlayerStat(nick, "tprounds");
        if (cash != Integer.MIN_VALUE) {
            showMsg(getMsg("player_all_stats"), formatNoPing(nick), cash, bank, net, bankrupts, winnings, rounds);
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }

    @Override
    public void showPlayerRank(String nick, String stat){
        if (getPlayerStat(nick, "exists") != 1){
            showMsg(getMsg("no_data"), formatNoPing(nick));
            return;
        }
        
        int highIndex, rank = 0;
        try {
            ArrayList<StatFileLine> statList = new ArrayList<StatFileLine>();
            loadPlayerFile(statList);
            ArrayList<String> nicks = new ArrayList<String>();
            ArrayList<Double> test = new ArrayList<Double>();
            int length = statList.size();
            String line = Colors.BLACK + ",08";
            
            for (int ctr = 0; ctr < statList.size(); ctr++) {
                nicks.add(statList.get(ctr).getNick());
            }
            
            if (stat.equals("cash")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get(stat));
                }
                line += "Cash: ";
            } else if (stat.equals("bank")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get(stat));
                }
                line += "Bank: ";
            } else if (stat.equals("bankrupts")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get(stat));
                }
                line += "Bankrupts: ";
            } else if (stat.equals("net") || stat.equals("netcash")) {
                for (int ctr = 0; ctr < nicks.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("netcash"));
                }
                line += "Net Cash: ";
            } else if (stat.equals("winnings")){
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("tpwinnings"));
                }
                line += "Texas Hold'em Winnings: ";
            } else if (stat.equals("rounds")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("tprounds"));
                }
                line += "Texas Hold'em Rounds: ";
            } else if (stat.equals("winrate")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    if (statList.get(ctr).get("tprounds") == 0){
                        test.add(0.);
                    } else {
                        test.add((double) statList.get(ctr).get("tpwinnings") / (double) statList.get(ctr).get("tprounds"));
                    }
                }
                line += "Texas Hold'em Win Rate: ";
            } else {
                throw new IllegalArgumentException();
            }
            
            // Find the player with the highest value, add to output string and remove.
            // Repeat n times or for the length of the list.
            for (int ctr = 1; ctr <= length; ctr++){
                highIndex = 0;
                rank++;
                for (int ctr2 = 0; ctr2 < nicks.size(); ctr2++) {
                    if (test.get(ctr2) > test.get(highIndex)) {
                        highIndex = ctr2;
                    }
                }
                if (nick.equalsIgnoreCase(nicks.get(highIndex))){
                    if (stat.equals("rounds") || stat.equals("bankrupts")) {
                        line += "#" + rank + " " + Colors.WHITE + ",04 " + formatNoPing(nick) + " " + formatNoDecimal(test.get(highIndex)) + " ";
                    } else if (stat.equals("winrate")) {
                        line += "#" + rank + " " + Colors.WHITE + ",04 " + formatNoPing(nick) + " $" + formatDecimal(test.get(highIndex)) + " ";
                    } else {
                        line += "#" + rank + " " + Colors.WHITE + ",04 " + formatNoPing(nick) + " $" + formatNoDecimal(test.get(highIndex)) + " ";
                    }
                    break;
                } else {
                    nicks.remove(highIndex);
                    test.remove(highIndex);
                }
            }
            showMsg(line);
        } catch (IOException e) {
            manager.log("Error reading players.txt!");
        }
    }
    
    @Override
    public void showTopPlayers(String stat, int n) {
        if (n < 1){
            throw new IllegalArgumentException();
        }
        
        int highIndex;
        try {
            ArrayList<StatFileLine> statList = new ArrayList<StatFileLine>();
            loadPlayerFile(statList);
            ArrayList<String> nicks = new ArrayList<String>();
            ArrayList<Double> test = new ArrayList<Double>();
            int length = Math.min(n, statList.size());
            String title = Colors.BOLD + Colors.BLACK + ",08 Top " + length;
            String list = Colors.BLACK + ",08";
            
            for (int ctr = 0; ctr < statList.size(); ctr++) {
                nicks.add(statList.get(ctr).getNick());
            }
            
            if (stat.equals("cash")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get(stat));
                }
                title += " Cash ";
            } else if (stat.equals("bank")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get(stat));
                }
                title += " Bank ";
            } else if (stat.equals("bankrupts")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get(stat));
                }
                title += " Bankrupts ";
            } else if (stat.equals("net") || stat.equals("netcash")) {
                for (int ctr = 0; ctr < nicks.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("netcash"));
                }
                title += " Net Cash ";
            } else if (stat.equals("winnings")){
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("tpwinnings"));
                }
                title += " Texas Hold'em Winnings ";
            } else if (stat.equals("rounds")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("tprounds"));
                }
                title += " Texas Hold'em Rounds ";
            } else if (stat.equals("winrate")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    if (statList.get(ctr).get("tprounds") == 0){
                        test.add(0.);
                    } else {
                        test.add((double) statList.get(ctr).get("tpwinnings") / (double) statList.get(ctr).get("tprounds"));
                    }
                }
                title += " Texas Hold'em Win Rate ";
            } else {
                throw new IllegalArgumentException();
            }

            showMsg(title);
            
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
                    list += " #" + ctr + ": " + Colors.WHITE + ",04 " + formatNoPing(nicks.get(highIndex)) + " " + formatNoDecimal(test.get(highIndex)) + " " + Colors.BLACK + ",08";
                } else if (stat.equals("winrate")) {
                    list += " #" + ctr + ": " + Colors.WHITE + ",04 " + formatNoPing(nicks.get(highIndex)) + " $" + formatDecimal(test.get(highIndex)) + " " + Colors.BLACK + ",08";
                } else {
                    list += " #" + ctr + ": " + Colors.WHITE + ",04 " + formatNoPing(nicks.get(highIndex)) + " $" + formatNoDecimal(test.get(highIndex)) + " " + Colors.BLACK + ",08";
                }
                nicks.remove(highIndex);
                test.remove(highIndex);
                if (nicks.isEmpty() || ctr == length) {
                    break;
                }
                // Output and reset after 10 players
                if (ctr % 10 == 0){
                    showMsg(list);
                    list = Colors.BLACK + ",08";
                }
            }
            showMsg(list);
        } catch (IOException e) {
            manager.log("Error reading players.txt!");
        }
    }
    
    /**
     * Displays the stack change of each player in the given list.
     */
    public void showStackChange() {
        PokerPlayer p;
        String msg = Colors.YELLOW + ",01 Change: " + Colors.NORMAL + " ";
        
        for (int ctr = 0; ctr < joined.size(); ctr++) {
            p = (PokerPlayer) joined.get(ctr);
            // Add player to list of net results
            if (p.get("change") > 0) {
                msg += p.getNick(false) + " (" + Colors.DARK_GREEN + Colors.BOLD + "$" + formatNumber(p.get("change")) + Colors.NORMAL + "), ";
            } else if (p.get("change") < 0) {
                msg += p.getNick(false) + " (" + Colors.RED + Colors.BOLD + "$" + formatNumber(p.get("change")) + Colors.NORMAL + "), ";
            } else {
                msg += p.getNick(false) + " (" + Colors.BOLD + "$" + formatNumber(p.get("change")) + Colors.NORMAL + "), ";
            }
        }
        
        showMsg(msg.substring(0, msg.length()-2));
    }
    
    /**
     * Displays the stack of each player in the given list in descending order.
     */
    public void showStacks() {
        ArrayList<Player> list = new ArrayList<Player>(joined);
        String msg = Colors.YELLOW + ",01 Stacks: " + Colors.NORMAL + " ";
        Collections.sort(list, Player.getCashComparator());
        
        for (Player p : list) {
            msg += p.getNick(false) + " (" + formatBold("$" + formatNumber(p.get("cash"))) + "), ";
        }
        
        showMsg(msg.substring(0, msg.length()-2));
    }
    
    /* Formatted strings */
    @Override
    public String getGameNameStr() {
        return formatBold(getMsg("tp_game_name"));
    }
    
    @Override
    public String getGameRulesStr() {
        return String.format(getMsg("tp_rules"), get("minbet")/2, get("minbet"));
    }
    
    @Override
    public String getGameStatsStr() {
        return String.format(getMsg("tp_stats"), getTotalPlayers(), getGameNameStr(), house);
    }
}
