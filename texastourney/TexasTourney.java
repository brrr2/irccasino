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

package irccasino.texastourney;

import irccasino.CardDeck;
import irccasino.GameManager;
import irccasino.StatFileLine;
import irccasino.cardgame.CardGame;
import irccasino.cardgame.Hand;
import irccasino.cardgame.Player;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import org.pircbotx.*;

public class TexasTourney extends CardGame {
    
    private ArrayList<PokerPot> pots;
    private PokerPot currentPot;
    private PokerPlayer dealer, smallBlind, bigBlind, topBettor;
    private Hand community;
    // In-game properties
    private int stage, currentBet, minRaise;
    
    /**
     * The default constructor for TexasPoker, subclass of CardGame.
     * This constructor loads the default INI file.
     * 
     * @param parent The bot that uses an instance of this class
     * @param commChar The command char
     * @param gameChannel The IRC channel in which the game is to be run.
     */
    public TexasTourney(GameManager parent, char commChar, Channel gameChannel){
        this(parent, commChar, gameChannel, "texastourney.ini");
    }
    
    /**
     * Allows a custom INI file to be loaded.
     * 
     * @param parent The bot that uses an instance of this class
     * @param commChar The command char
     * @param gameChannel The IRC channel in which the game is to be run.
     * @param customINI the file path to a custom INI file
     */
    public TexasTourney(GameManager parent, char commChar, Channel gameChannel, String customINI) {
        super(parent, commChar, gameChannel);
        name = "texastourney";
        iniFile = customINI;
        helpFile = "texastourney.help";
        strFile = "strlib.txt";
        loadStrLib(strFile);
        loadHelp(helpFile);
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
                showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), currentPlayer.get("bet"), 
                        currentPlayer.get("cash")-currentPlayer.get("bet"), currentBet, getCashInPlay());
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
                loadHelp(helpFile);
                loadStrLib(strFile);
                showMsg(getMsg("reload"));
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
        // Do nothing
    }
    
    @Override
    public void startRound() {
        if (joined.size() < 2){
            endRound();
        } else {
            setButton();
            showTablePlayers();
            dealTable();
            setBlindBets();
            currentPlayer = getPlayerAfter(bigBlind);
            showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), currentPlayer.get("bet"), 
                        currentPlayer.get("cash")-currentPlayer.get("bet"), currentBet, getCashInPlay());
            setIdleOutTask();
        }
    }
    
    @Override
    public void continueRound() {
        // Store currentPlayer as firstPlayer and find the next player
        Player firstPlayer = currentPlayer;
        currentPlayer = getPlayerAfter(currentPlayer);
        PokerPlayer p = (PokerPlayer) currentPlayer;
        PokerSimulator sim;
        
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
                // Burn a card before turn and river
                if (stage != 1) {
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
                        currentPlayer.get("cash")-currentPlayer.get("bet"), currentBet, getCashInPlay());
            setIdleOutTask();
        }
    }
    
    @Override
    public void endRound() {
        roundEnded = true;
        PokerPlayer p;

        // Check if anybody left during post-start waiting period
        if (joined.size() > 1 && pots.size() > 0) {
            String netResults = Colors.YELLOW + ",01 Change: ";
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

            /* Clean-up tasks
             * 1. Increment the number of rounds played for player
             * 2. Remove players who have quit mid-round
             * 3. Save player data
             * 4. Reset the player
             */
            for (int ctr = 0; ctr < joined.size(); ctr++){
                p = (PokerPlayer) joined.get(ctr);
                p.increment("tprounds");
                
                // Bankrupts
                if (!p.has("cash")) {
                    // Show that player has gone bankrupt and has been removed
                    // from the tournament
                    blacklist.add(p);
                    removeJoined(p);
                    ctr--;
                // Quitters
                } else if (p.has("quit")) {
                    removeJoined(p);
                    ctr--;
                }
                
                // Add player to list of net results
                if (p.get("change") > 0) {
                     netResults += Colors.WHITE + ",01 " + p.getNick() + " (" + Colors.GREEN + ",01$" + formatNumber(p.get("change")) + Colors.WHITE + ",01), ";
                } else if (p.get("change") < 0) {
                    netResults += Colors.WHITE + ",01 " + p.getNick() + " (" + Colors.RED + ",01$" + formatNumber(p.get("change")) + Colors.WHITE + ",01), ";
                } else {
                    netResults += Colors.WHITE + ",01 " + p.getNick() + " (" + Colors.WHITE + ",01$" + formatNumber(p.get("change")) + Colors.WHITE + ",01), ";
                }
                resetPlayer(p);
            }
            showMsg(netResults.substring(0, netResults.length()-2));
        } else {
            showMsg(getMsg("no_players"));
        }
        resetGame();
        showMsg(getMsg("end_round"), getGameNameStr(), commandChar);
        
        // Nobody wins if everybody leaves
        if (joined.isEmpty()) {
            showMsg(getMsg("tt_no_winner"));
        // Declare a winner if only one player is left
        } else if (joined.size() == 1) {
            p = (PokerPlayer) joined.get(0);
            p.add("ttwins", 1);
            p.add("ttrounds", 1);
            showMsg(getMsg("tt_winner"), p.getNickStr(), p.get("ttwins"));
            for (int ctr = 0; ctr < blacklist.size(); ctr++) {
                p = (PokerPlayer) blacklist.get(ctr);
                p.add("ttrounds", 1);
            }
            blacklist.clear();
        // Automatically start a new round if more than 1 player left
        } else {
            showStartRound();
            setStartRoundTask();
        }
    }
    
    @Override
    public void endGame() {
        cancelStartRoundTask();
        cancelIdleOutTask();
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
        devoiceAll();
        showMsg(getMsg("game_end"), getGameNameStr());
        joined.clear();
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
        } else {
            informPlayer(nick, getMsg("no_join"));
        }
    }
    
    /**
     * Returns next player after the specified player.
     * @param p the specified player
     * @return the next player
     */
    private Player getPlayerAfter(Player p){
        return joined.get((joined.indexOf(p) + 1) % joined.size());
    }
    
    /**
     * Resets the specified player.
     * @param p the player to reset
     */
    private void resetPlayer(PokerPlayer p) {
        discardPlayerHand(p);
        p.clear("fold");
        p.clear("quit");
        p.clear("allin");
        p.clear("change");
    }
    
    /**
     * Assigns players to the dealer, small blind and big blind roles.
     */
    private void setButton(){
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
    private void setBlindBets(){
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
        settings.put("cash", 1000);
        settings.put("idle", 60);
        settings.put("idlewarning", 45);
        settings.put("maxplayers", 22);
        settings.put("minplayers", 5);
        settings.put("minbet", 10);
        settings.put("startwait", 5);
        settings.put("showdown", 10);
        settings.put("doubleblinds", 10);
        // In-game properties
        stage = 0;
        currentBet = 0;
        minRaise = 0;
    }
    
    @Override
    protected final void saveIniFile() {
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(iniFile)));
            out.println("#Settings");
            out.println("#Number of seconds before a player idles out");
            out.println("idle=" + get("idle"));
            out.println("#Number of seconds before a player is given a warning for idling");
            out.println("idlewarning=" + get("idlewarning"));
            out.println("#Initial amount given to new and bankrupt players");
            out.println("cash=" + get("cash"));
            out.println("#Minimum bet (big blind), preferably an even number");
            out.println("minbet=" + get("minbet"));
            out.println("#The maximum number of players allowed to join a tournament");
            out.println("maxplayers=" + get("maxplayers"));
            out.println("#The minimum number of players required to start a tournament");
            out.println("minplayers=" + get("maxplayers"));
            out.println("#The wait time in seconds after the start command is given");
            out.println("startwait=" + get("startwait"));
            out.println("#The wait time in seconds in between reveals during a showdown");
            out.println("showdown=" + get("showdown"));
            out.println("#The number of rounds in between doubling of blinds");
            out.println("doubleblinds=" + get("doubleblinds"));
            out.close();
        } catch (IOException e) {
            manager.log("Error creating " + iniFile + "!");
        }
    }
    
    /* House stats management */
    @Override
    public final void loadGameStats() {
        // Do nothing
    }
    
    @Override
    public void saveGameStats() {
        // Do nothing
    }
    
    /* Card management methods for Texas Hold'em Poker */
    
    /**
     * Deals cards to the community hand.
     */
    private void dealCommunity(){
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
    private void dealHand(PokerPlayer p) {
        dealCard(p.getHand());
        dealCard(p.getHand());
    }
    
    /**
     * Deals hands to everybody at the table.
     */
    private void dealTable() {
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
    private void discardPlayerHand(PokerPlayer p) {
        if (p.hasHand()) {
            deck.addToDiscard(p.getHand());
            p.resetHand();
        }
    }
    
    /**
     * Discards the community cards into the discard pile.
     */
    private void discardCommunity(){
        if (community.size() > 0){
            deck.addToDiscard(community);
            community.clear();
        }
    }
    
    /**
     * Merges the discards and shuffles the deck.
     */
    private void shuffleDeck() {
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
            showMsg(getMsg("tp_allin"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
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
                showMsg(getMsg("tp_check"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
            } else {
                p.set("bet", amount);
                showMsg(getMsg("tp_call"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
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
                showMsg(getMsg("tp_bet"), p.getNickStr(), p.get("bet"), p.get("cash") - p.get("bet"));
            } else {
                showMsg(getMsg("tp_raise"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
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
            showMsg(getMsg("tp_check"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
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
    private int getNumberNotFolded(){
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
    private int getNumberCanBet(){
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
    private int getNumberBettors() {
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
    private int getCashInPlay() {
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
    private void addBetsToPot(){
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
        return Integer.MIN_VALUE;
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
            str = Colors.YELLOW+",01Pot #"+(ctr+1)+": "+Colors.GREEN+",01$"+formatNumber(pots.get(ctr).getTotal())+Colors.NORMAL+" ";
            msg.append(str);
        }
        
        // Append remaining non-folded players
        int notFolded = getNumberNotFolded();
        int count = 0;
        str = "(" + formatBold(notFolded) + " players: ";
        for (int ctr = 0; ctr < joined.size(); ctr++){
            p = (PokerPlayer) joined.get(ctr);
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
                showMsg(getMsg("tp_player_result"), p.getNickStr(), p.getHand(), p.getPokerHand().getName(), p.getPokerHand());
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
                p.add("change", currentPot.getTotal()/winners);
                showMsg(Colors.YELLOW+",01 Pot #" + (ctr+1) + ": " + Colors.NORMAL + " " + 
                    p.getNickStr() + " wins $" + formatNumber(currentPot.getTotal()/winners) + 
                    ". Stack: $" + formatNumber(p.get("cash"))+ " (" + getPlayerListString(currentPot.getPlayers()) + ")");
            }
        }
    }
    
    @Override
    public void showPlayerWinnings(String nick){
        // Do nothing
    }
    
    @Override
    public void showPlayerWinRate(String nick){
        // Do nothing
    }
    
    @Override
    public void showPlayerRounds(String nick){
        // Do nothing
    }
    
    @Override
    public void showPlayerAllStats(String nick){
        // Do nothing
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
            
            if (stat.equals("wins")){
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("ttwins"));
                }
                line += "Texas Hold'em Tournament Wins: ";
            } else if (stat.equals("rounds")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("ttrounds"));
                }
                line += "Texas Hold'em Tournaments Played: ";
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
                    line += "#" + rank + " " + Colors.WHITE + ",04 " + formatNoPing(nick) + " " + formatNoDecimal(test.get(highIndex)) + " ";
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
            
            if (stat.equals("wins")){
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("ttwins"));
                }
                title += " Texas Hold'em Tournament Wins ";
            } else if (stat.equals("rounds")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("ttrounds"));
                }
                title += " Texas Hold'em Tournaments Played ";
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
                
                list += " #" + ctr + ": " + Colors.WHITE + ",04 " + formatNoPing(nicks.get(highIndex)) + " " + formatNoDecimal(test.get(highIndex)) + " " + Colors.BLACK + ",08";
                
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
    
    /* Formatted strings */
    @Override
    public final String getGameNameStr() {
        return formatBold(getMsg("tt_game_name"));
    }
    
    @Override
    public final String getGameRulesStr() {
        return String.format(getMsg("tt_rules"), get("minbet")/2, get("minbet"));
    }
    
    @Override
    public final String getGameStatsStr() {
        // Do nothing
        return null;
    }
}
