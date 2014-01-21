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

import irccasino.cardgame.CardDeck;
import irccasino.GameManager;
import irccasino.StatFileLine;
import irccasino.cardgame.CardGame;
import irccasino.cardgame.Hand;
import irccasino.cardgame.Player;
import irccasino.texaspoker.PokerPot;
import irccasino.texaspoker.PokerPlayer;
import irccasino.texaspoker.PokerSimulator;
import irccasino.texaspoker.TexasPoker;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import org.pircbotx.*;

/**
 * Extends TexasPoker to work as a tournament mode.
 * @author Yizhe Shen
 */
public class TexasTourney extends TexasPoker {
    
    ArrayList<Player> newOutList;
    int tourneyRounds, numOuts;
    boolean newPlayerOut;
    
    public TexasTourney() {
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
        manager = parent;
        commandChar = commChar;
        channel = gameChannel;
        joined = new ArrayList<Player>();
        blacklist = new ArrayList<Player>();
        waitlist = new ArrayList<Player>();
        settings = new HashMap<String,Integer>();
        cmdMap = new HashMap<String,String>();
        opCmdMap = new HashMap<String,String>();
        aliasMap = new HashMap<String,String>();
        msgMap = new HashMap<String,String>();
        gameTimer = new Timer("Game Timer");
        startRoundTask = null;
        idleOutTask = null;
        idleWarningTask = null;
        currentPlayer = null;
        checkPlayerFile();
        name = "texastourney";
        iniFile = customINI;
        helpFile = "texastourney.help";
        strFile = "strlib.txt";
        loadStrLib(strFile);
        loadHelp(helpFile);
        initialize();
        loadIni();
        deck = new CardDeck();
        deck.shuffleCards();
        pots = new ArrayList<PokerPot>();
        newOutList = new ArrayList<Player>();
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
        
        /* Parsing commands from the channel */
        if (command.equals("join") || command.equals("j")){
            join(nick, host);
        } else if (command.equals("leave") || command.equals("quit") || command.equals("l") || command.equals("q")){
            leave(nick);
        } else if (command.equals("start") || command.equals("go")) {
            if (isStartAllowed(nick)){
                inProgress = true;
                showMsg(formatHeader(" " + getMsg("tt_new_tourney") + " "));
                showStartRound();
                setStartRoundTask();
            }
        } else if (command.equals("cash")) {
            if (!inProgress) {
                informPlayer(nick, getMsg("tt_no_start"));
            } else {
                if (params.length > 0){
                    showPlayerCash(params[0]);
                } else {
                    showPlayerCash(nick);
                }
            }
        } else if (command.equals("tourneys")) {
            if (params.length > 0){
                showPlayerTourneysPlayed(params[0]);
            } else {
                showPlayerTourneysPlayed(nick);
            }
        } else if (command.equals("wins")) {
            if (params.length > 0){
                showPlayerTourneyWins(params[0]);
            } else {
                showPlayerTourneyWins(nick);
            }
        } else if (command.equals("player") || command.equals("p")){
            if (params.length > 0){
                showPlayerAllStats(params[0]);
            } else {
                showPlayerAllStats(nick);
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
        } else if (command.equals("blacklist")) {
            if (!inProgress) {
                showMsg(getMsg("tt_no_start"));
            } else {
                showMsg(getMsg("tt_out_of_tourney"), getPlayerListString(blacklist));
            }
        } else if (command.equals("rank")) {
            if (inProgress) {
                informPlayer(nick, getMsg("wait_round_end"));
            } else {
                if (params.length > 1){
                    try {
                        showPlayerRank(params[1].toLowerCase(), params[0].toLowerCase());
                    } catch (IllegalArgumentException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else if (params.length == 1){
                    try {
                        showPlayerRank(nick, params[0].toLowerCase());
                    } catch (IllegalArgumentException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    showPlayerRank(nick, "cash");
                }
            }
        } else if (command.equals("top")) {
            if (inProgress) {
                informPlayer(nick, getMsg("wait_round_end"));
            } else {
                if (params.length > 1){
                    try {
                        showTopPlayers(params[1].toLowerCase(), Integer.parseInt(params[0]));
                    } catch (IllegalArgumentException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else if (params.length == 1){
                    try {
                        showTopPlayers("wins", Integer.parseInt(params[0]));
                    } catch (IllegalArgumentException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    showTopPlayers("wins", 5);
                }
            }
        } else if (command.equals("simple")) {
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else {
                togglePlayerSimple(nick);
            }
        } else if (command.equals("stats")){
            if (inProgress) {
                informPlayer(nick, getMsg("wait_round_end"));
            } else {
                showMsg(getGameStatsStr());
            }
        } else if (command.equals("grules")) {
            informPlayer(nick, getGameRulesStr());
        } else if (command.equals("ghelp")) {
            if (params.length == 0){
                informPlayer(nick, getMsg("game_help"), commandChar, commandChar, commandChar);
            } else {
                informPlayer(nick, getCommandHelp(params[0].toLowerCase()));
            }
        } else if (command.equals("gcommands")) {
            informPlayer(nick, getGameNameStr() + " commands:");
            informPlayer(nick, getCommandsStr());
            if (channel.isOp(user)) {
                informPlayer(nick, getGameNameStr() + " Op commands:");
                informPlayer(nick, getOpCommandsStr());
            }
        } else if (command.equals("game")) {
            showMsg(getMsg("game_name"), getGameNameStr());
        /* Op commands */
        } else if (command.equals("fj") || command.equals("fjoin")){
            if (!channel.isOp(user)) {
                informPlayer(nick, getMsg("ops_only"));
            } else {
                if (params.length > 0){
                    String fNick = params[0];
                    Iterator<User> it = channel.getUsers().iterator();
                    while(it.hasNext()){
                        User u = it.next();
                        if (u.getNick().equalsIgnoreCase(fNick)){
                            fjoin(nick, u.getNick(), u.getHostmask());
                            return;
                        }
                    }
                    informPlayer(nick, getMsg("nick_not_found"), fNick);
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equals("fl") || command.equals("fq") || command.equals("fquit") || command.equals("fleave")){
            if (!channel.isOp(user)) {
                informPlayer(nick, getMsg("ops_only"));
            } else {
                if (params.length > 0){
                    leave(params[0]);
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equals("fstart") || command.equals("fgo")){
            if (isForceStartAllowed(user,nick)){
                inProgress = true;
                showMsg(formatHeader(getMsg("tt_new_tourney")));
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
                resetTourney();
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
        } else if (command.equals("settings")) {
            if (isOpCommandAllowed(user, nick)) {
                informPlayer(nick, getGameNameStr() + " settings:");
                informPlayer(nick, getSettingsStr());
            }
        } else if (command.equals("set")){
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 1){
                    try {
                        set(params[0].toLowerCase(), Integer.parseInt(params[1]));
                        saveIniFile();
                        showMsg(getMsg("setting_updated"), params[0].toLowerCase());
                    } catch (IllegalArgumentException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equals("get")) {
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        showMsg(getMsg("setting"), params[0].toLowerCase(), get(params[0].toLowerCase()));
                    } catch (IllegalArgumentException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        }
    }
    
    /**
     * If a joined player changes nick, then unfortunately they are out of the
     * tournament.
     * @param user
     * @param oldNick
     * @param newNick 
     */
    @Override
    protected void processNickChange(User user, String oldNick, String newNick){
        if (isJoined(oldNick)){
            informPlayer(newNick, getMsg("tt_nick_change"));
            manager.deVoice(channel, user);
            leave(oldNick);
        }
    }

    /* Game management methods */
    @Override
    public void addPlayer(String nick, String host) {
        addPlayer(new PokerPlayer(nick, host));
    }
    
    @Override
    protected void removeJoined(Player p){
        User user = findUser(p.getNick());
        joined.remove(p);
        if (inProgress) {
            showMsg(getMsg("tt_unjoin"), p.getNickStr());
        } else {
            if (user != null){
                manager.deVoice(channel, user);
            }
            showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
        }
    }
    
    @Override
    protected void devoiceAll(){
        String modeSet = "";
        String nickStr = "";
        int count = 0;
        for (Player p : joined) {
            modeSet += "v";
            nickStr += " " + p.getNick();
            count++;
            if (count % 4 == 0) {
                manager.sendRawLine ("MODE " + channel.getName() + " -" + modeSet + nickStr);
                nickStr = "";
                modeSet = "";
                count = 0;
            }
        }
        for (Player p : blacklist) {
            modeSet += "v";
            nickStr += " " + p.getNick();
            count++;
            if (count % 4 == 0) {
                manager.sendRawLine ("MODE " + channel.getName() + " -" + modeSet + nickStr);
                nickStr = "";
                modeSet = "";
                count = 0;
            }
        }
        if (count > 0) {
            manager.sendRawLine ("MODE " + channel.getName() + " -" + modeSet + nickStr);
        }
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
            // Reset minimum raise (override)
            minRaise = (int) (get("minbet")*(Math.pow(2, tourneyRounds/get("doubleblinds"))));
            
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
                
                // Burn a card before flop, turn and river
                burnCard();
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
            showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), currentPlayer.get("cash")-currentPlayer.get("bet"),
                        currentPlayer.get("bet"), currentBet, getCashInPlay());
            setIdleOutTask();
        }
    }
    
    @Override
    public void endRound() {
        PokerPlayer p;
        
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

            // Show player stack changes
            showStackChange();
            
            // Show updated player stacks sorted in descending order
            showStacks();
            
            /* Clean-up tasks necessarily in this order
             * 1. Remove players who have quit mid-round or have gone bankrupt
             * 2. Reset all players
             */
            for (int ctr = 0; ctr < joined.size(); ctr++){
                p = (PokerPlayer) joined.get(ctr);
                
                // Bankrupts
                if (!p.has("cash")) {
                    // Show that player has gone bankrupt and has been removed
                    // from the tournament
                    blacklist.add(0, p);
                    removeJoined(p);
                    ctr--;
                    newPlayerOut = true;
                    newOutList.add(p);
                // Quitters
                } else if (p.has("quit")) {
                    removeJoined(p);
                    blacklist.add(0, p);
                    ctr--;
                }
                
                resetPlayer(p);
            } 
        }
        resetGame();
        tourneyRounds++;        
        showMsg(getMsg("tt_end_round"), tourneyRounds);
        checkTourneyStatus();
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
        newOutList.clear();
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
    
    public void resetTourney() {
        inProgress = false;
        tourneyRounds = 0;
        numOuts = 0;
        newPlayerOut = false;
        newOutList.clear();
        devoiceAll();
        joined.clear();
        blacklist.clear();
    }
    
    public void checkTourneyStatus() {
        PokerPlayer p;
        
        // Nobody wins if everybody leaves
        if (joined.isEmpty()) {
            showMsg(getMsg("tt_no_winner"));
            resetTourney();
        // Declare a winner if only one player is left
        } else if (joined.size() == 1) {
            p = (PokerPlayer) joined.get(0);
            p.add("ttwins", 1);
            p.add("ttplayed", 1);
            savePlayerData(p);
            showMsg(getMsg("tt_winner"), p.getNickStr(), p.get("ttwins"));
            for (int ctr = 0; ctr < blacklist.size(); ctr++) {
                p = (PokerPlayer) blacklist.get(ctr);
                p.add("ttplayed", 1);
                savePlayerData(p);
            }
            
            // Display tournament results
            showTourneyResults();
            
            resetTourney();
        // Automatically start a new round if more than 1 player left
        } else {
            if (newPlayerOut) {
                numOuts++;
                int newBlind = (int) (get("minbet")*(Math.pow(2, tourneyRounds/get("doubleblinds") + numOuts)));
                if (newOutList.size() > 1) {
                    String nicks = "";
                    for (Player o : newOutList) {
                        nicks += o.getNickStr(false) + ", ";
                    }
                    showMsg(getMsg("tt_double_blinds_multi_out"), nicks.substring(0, nicks.length()-2), newBlind/2, newBlind);
                } else {
                    showMsg(getMsg("tt_double_blinds_single_out"), newOutList.get(0).getNickStr(false), newBlind/2, newBlind);
                }
                newPlayerOut = false;
                newOutList.clear();
            }
            if (tourneyRounds % get("doubleblinds") == 0) {
                int newBlind = (int) (get("minbet")*(Math.pow(2, tourneyRounds/get("doubleblinds") + numOuts)));
                showMsg(getMsg("tt_double_blinds"), tourneyRounds, newBlind/2, newBlind);
            }
            showStartRound();
            setStartRoundTask();
        }
    }
    
    @Override
    public void join(String nick, String host) {
        CardGame game = manager.getGame(nick);
        if (joined.size() == get("maxplayers")){
            informPlayer(nick, getMsg("max_players"));
        } else if (isJoined(nick)) {
            informPlayer(nick, getMsg("is_joined"));
        } else if (isBlacklisted(nick) || manager.isBlacklisted(nick)) {
            informPlayer(nick, getMsg("on_blacklist"));
        } else if (game != null) {
            informPlayer(nick, getMsg("is_joined_other"), game.getGameNameStr(), game.getChannel().getName());
        } else if (inProgress) {
            informPlayer(nick, getMsg("tt_started_unable_join"));
        } else {
            addPlayer(nick, host);
        }
    }
    
    @Override
    public void fjoin(String OpNick, String nick, String host) {
        CardGame game = manager.getGame(nick);
        if (joined.size() == get("maxplayers")){
            informPlayer(OpNick, getMsg("max_players"));
        } else if (isJoined(nick)) {
            informPlayer(OpNick, getMsg("is_joined_nick"), nick);
        } else if (isBlacklisted(nick) || manager.isBlacklisted(nick)) {
            informPlayer(OpNick, getMsg("on_blacklist_nick"), nick);
        } else if (game != null) {
            informPlayer(OpNick, getMsg("is_joined_other_nick"), nick, game.getGameNameStr(), game.getChannel().getName());
        } else if (inProgress) {
            informPlayer(OpNick, getMsg("tt_started_unable_join"));
        } else {
            addPlayer(nick, host);
        }
    }
    
    @Override
    public void leave(String nick) {
        // Check if the nick is even joined
        if (isJoined(nick)){
            PokerPlayer p = (PokerPlayer) findJoined(nick);
            // Check if a tournament is in progress
            if (inProgress) {
                // If still in the post-start-round waiting phase, then 
                // currentPlayer has not been set yet.
                if (currentPlayer == null){
                    removeJoined(p);
                    blacklist.add(0, p);
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
     * Sets the bets for the small and big blinds.
     */
    @Override
    protected void setBlindBets(){
        // Calculate the current blind bet
        int newBlind = (int) (get("minbet")*(Math.pow(2, tourneyRounds/get("doubleblinds") + numOuts)));
        // Set the small blind to minimum raise or the player's cash, 
        // whichever is less.
        smallBlind.set("bet", Math.min(newBlind/2, smallBlind.get("cash")));
        // Set the big blind to minimum raise + small blind or the player's 
        // cash, whichever is less.
        bigBlind.set("bet", Math.min(newBlind, bigBlind.get("cash")));
        // Set the current bet to the bigger of the two blinds.
        currentBet = Math.max(smallBlind.get("bet"), bigBlind.get("bet"));
        minRaise = newBlind;
    }
    
    /* Game command logic checking methods */
    @Override
    public boolean isStartAllowed(String nick){
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (inProgress) {
            informPlayer(nick, getMsg("round_started"));
        } else if (joined.size() < get("minplayers")) {
            showMsg(getMsg("no_players"));
        } else {
            return true;
        }
        return false;
    }

    @Override
    public boolean isForceStartAllowed(User user, String nick){
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (inProgress) {
            informPlayer(nick, getMsg("round_started"));
        } else if (joined.size() < get("minplayers")) {
            showMsg(getMsg("no_players"));
        } else {
            return true;
        }
        return false;
    }
    
    /**
     * Checks if an Op command is allowed.
     * @param user command issuer
     * @param nick the user's nick
     * @return true if the User is allowed use Op commands
     */
    @Override
    protected boolean isOpCommandAllowed(User user, String nick){
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (inProgress) {
            informPlayer(nick, getMsg("tt_wait_for_end"));
        } else {
            return true;
        }
        return false;
    }
    
    /* Game settings management */
    @Override
    protected void initialize(){
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
        settings.put("doubleonbankrupt", 0);
        // In-game properties
        stage = 0;
        currentBet = 0;
        minRaise = 0;
        tourneyRounds = 0;
        newPlayerOut = false;
        numOuts = 0;
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
            out.println("#Minimum bet (big blind), preferably an even number");
            out.println("minbet=" + get("minbet"));
            out.println("#The maximum umber of players allowed to join a tournament");
            out.println("maxplayers=" + get("maxplayers"));
            out.println("#The minimum number of players required to start a tournament");
            out.println("minplayers=" + get("minplayers"));
            out.println("#The wait time in seconds after the start command is given");
            out.println("startwait=" + get("startwait"));
            out.println("#The wait time in seconds in between reveals during a showdown");
            out.println("showdown=" + get("showdown"));
            out.println("#The number of rounds in between doubling of blinds");
            out.println("doubleblinds=" + get("doubleblinds"));
            out.println("#Whether or not to double blinds when a player goes out");
            out.println("doubleonbankrupt=" + get("doubleonbankrupt"));
            out.close();
        } catch (IOException e) {
            manager.log("Error creating " + iniFile + "!");
        }
    }
    
    @Override
    protected void loadPlayerData(Player p) {
        try {
            boolean found = false;
            ArrayList<StatFileLine> statList = new ArrayList<StatFileLine>();
            loadPlayerFile(statList);

            for (StatFileLine statLine : statList) {
                if (p.getNick().equalsIgnoreCase(statLine.getNick())) {
                    p.set("cash", get("cash"));
                    p.set("ttwins", statLine.get("ttwins"));
                    p.set("ttplayed", statLine.get("ttplayed"));
                    p.set("simple", statLine.get("simple"));
                    found = true;
                    break;
                }
            }
            if (!found) {
                p.set("cash", get("cash"));
            }
        } catch (IOException e) {
            manager.log("Error reading players.txt!");
        }
    }
    
    @Override
    protected void savePlayerData(Player p){
        boolean found = false;
        ArrayList<StatFileLine> statList = new ArrayList<StatFileLine>();
        
        try {
            loadPlayerFile(statList);
            for (StatFileLine statLine : statList) {
                if (p.getNick().equalsIgnoreCase(statLine.getNick())) {
                    statLine.set("ttwins", p.get("ttwins"));
                    statLine.set("ttplayed", p.get("ttplayed"));
                    statLine.set("simple", p.get("simple"));
                    found = true;
                    break;
                }
            }
            if (!found) {
                statList.add(new StatFileLine(p.getNick(), p.get("cash"),
                                        p.get("bank"), p.get("bankrupts"),
                                        p.get("bjwinnings"), p.get("bjrounds"),
                                        p.get("tpwinnings"), p.get("tprounds"),
                                        p.get("ttwins"), p.get("ttplayed"),
                                        p.get("simple")));
            }
        } catch (IOException e) {
            manager.log("Error reading players.txt!");
        }

        try {
            savePlayerFile(statList);
        } catch (IOException e) {
            manager.log("Error writing to players.txt!");
        }
    }
    
    /* House stats management */
    @Override
    public void loadGameStats() {
        throw new UnsupportedOperationException("Not supported");
    }
    
    @Override
    public void saveGameStats() {
        throw new UnsupportedOperationException("Not supported");
    }
    
    @Override
    public int getTotalPlayers(){
        try {
            ArrayList<StatFileLine> statList = new ArrayList<StatFileLine>();
            loadPlayerFile(statList);
            int total = 0;
            
            for (StatFileLine statLine : statList) {
                if (statLine.has("ttplayed")){
                    total++;
                }
            }
            return total;
        } catch (IOException e){
            manager.log("Error reading players.txt!");
            return 0;
        }
    }    
    
    /* Channel message output methods for Texas Hold'em Tournament*/
    @Override
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
        }
    }
    
    /**
     * Displays the final results of a tournament.
     */
    public void showTourneyResults() {
        String msg = "";
        
        // Append title
        msg += formatHeader(" Final Standings: ") + " ";
        
        // Append winner
        msg += " " + formatBold("#1:") + " " + joined.get(0).getNickStr() + " ";
        
        // Append the other players in the order in which they placed
        for (int ctr = 0; ctr < blacklist.size(); ctr++) {
            msg += " " + formatBold("#" + (ctr + 2) + ":") + " " + blacklist.get(ctr).getNickStr() + " ";
        }

        showMsg(msg);
    }    
    
    @Override
    protected void showStartRound(){
        showMsg(getMsg("tt_start_round"), tourneyRounds + 1, get("startwait"));
    }
    
    public void showPlayerTourneysPlayed(String nick){
        int ttplayed = getPlayerStat(nick, "ttplayed");
        if (ttplayed != Integer.MIN_VALUE){
            showMsg(getMsg("tt_player_played"), formatNoPing(nick), ttplayed);
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }
    
    public void showPlayerTourneyWins(String nick){
        int ttwins = getPlayerStat(nick, "ttwins");
        if (ttwins != Integer.MIN_VALUE){
            showMsg(getMsg("tt_player_wins"), formatNoPing(nick), ttwins);
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }
    
    @Override
    public void showPlayerAllStats(String nick){
        int ttwins = getPlayerStat(nick, "ttwins");
        int ttplayed = getPlayerStat(nick, "ttplayed");
        if (ttwins != Integer.MIN_VALUE) {
            showMsg(getMsg("tt_player_all_stats"), formatNoPing(nick), ttwins, ttplayed);
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
            
            if (stat.equals("wins")){
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("ttwins"));
                }
                line += "Texas Hold'em Tournament Wins: ";
            } else if (stat.equals("tourneys")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("ttplayed"));
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
            } else if (stat.equals("tourneys")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("ttplayed"));
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
    
    @Override
    public void showStacks() {
        ArrayList<Player> list = new ArrayList<Player>(joined);
        String msg = Colors.YELLOW + ",01 Stacks: " + Colors.NORMAL + " ";
        Collections.sort(list, Player.getCashComparator());
        
        // Add players still in the tournament
        for (Player p : list) {
            if (p.get("cash") == 0) {
                msg += p.getNick(false) + " (" + Colors.RED + formatBold("OUT") + Colors.NORMAL + "), ";
            } else {
                msg += p.getNick(false) + " (" + formatBold("$" + formatNumber(p.get("cash"))) + "), ";
            }
        }
        
        // Add players who are no longer in the tournament
        for (Player p : blacklist) {
            msg += p.getNick(false) + " (" + Colors.RED + formatBold("OUT") + Colors.NORMAL + "), ";
        }
        
        showMsg(msg.substring(0, msg.length()-2));
    }
    
    /* Formatted strings */
    @Override
    public String getGameNameStr() {
        return formatBold(getMsg("tt_game_name"));
    }
    
    @Override
    public String getGameRulesStr() {
        return String.format(getMsg("tt_rules"), get("minbet")/2, get("minbet"));
    }
    
    @Override
    public String getGameStatsStr() {
        return String.format(getMsg("tt_stats"), getTotalPlayers(), getGameNameStr());
    }
}