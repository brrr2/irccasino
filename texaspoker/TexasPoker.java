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
import irccasino.cardgame.PlayerRecord;
import irccasino.GameManager;
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
    
    public enum PokerState {
        NONE, PRE_START, BLINDS, BETTING, SHOWDOWN, CONTINUE_ROUND, END_ROUND
    }
    
    public enum PokerBet {
        NONE, PRE_FLOP, FLOP, TURN, RIVER;
        private static final PokerBet[] vals = values();
        
        public PokerBet next() {
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }
    
    protected ArrayList<PokerPot> pots;
    protected PokerPot currentPot;
    protected PokerPlayer dealer;
    protected PokerPlayer smallBlind;
    protected PokerPlayer bigBlind;
    protected PokerPlayer topBettor;
    protected Hand community;
    protected HouseStat house;
    // In-game properties
    protected PokerState state;
    protected PokerBet betState;
    protected int currentBet;
    protected int minRaise;
    
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
        super(parent, commChar, gameChannel, customINI);
    }
    
    /////////////////////////////////////////
    //// Methods that process IRC events ////
    /////////////////////////////////////////
    @Override
    public void processCommand(User user, String command, String[] params){
        String nick = user.getNick();
        String host = user.getHostmask();
        
        // Commands available in TexasPoker.
        if (command.equalsIgnoreCase("join") || command.equalsIgnoreCase("j")){
            join(nick, host);
        } else if (command.equalsIgnoreCase("leave") || command.equalsIgnoreCase("quit") || command.equalsIgnoreCase("l") || command.equalsIgnoreCase("q")){
            leave(nick, params);
        } else if (command.equalsIgnoreCase("last")) {
            last(nick, params);
        } else if (command.equalsIgnoreCase("start") || command.equalsIgnoreCase("go")) {
            start(nick, params);
        } else if (command.equalsIgnoreCase("stop")) {
            stop(nick, params);
        } else if (command.equalsIgnoreCase("bet") || command.equalsIgnoreCase("b")) {
            bet(nick, params);
        } else if (command.equalsIgnoreCase("c") || command.equalsIgnoreCase("ca") || command.equalsIgnoreCase("call")) {
            call(nick, params);
        } else if (command.equalsIgnoreCase("x") || command.equalsIgnoreCase("ch") || command.equalsIgnoreCase("check")) {
            check(nick, params);
        } else if (command.equalsIgnoreCase("fold") || command.equalsIgnoreCase("f")) {
            fold(nick, params);
        } else if (command.equalsIgnoreCase("raise") || command.equalsIgnoreCase("r")) {
            raise(nick, params);
        } else if (command.equalsIgnoreCase("allin") || command.equalsIgnoreCase("a")){
            allin(nick, params);
        } else if (command.equalsIgnoreCase("community") || command.equalsIgnoreCase("comm")){
            community(nick, params);
        } else if (command.equalsIgnoreCase("hand")) {
            hand(nick, params);
        } else if (command.equalsIgnoreCase("turn")) {
            turn(nick, params);
        } else if (command.equalsIgnoreCase("cash") || command.equalsIgnoreCase("stack")) {
            cash(nick, params);
        } else if (command.equalsIgnoreCase("netcash") || command.equalsIgnoreCase("net")) {
            netcash(nick, params);
        } else if (command.equalsIgnoreCase("bank")) {
            bank(nick, params);
        } else if (command.equalsIgnoreCase("bankrupts")) {
            bankrupts(nick, params);
        } else if (command.equalsIgnoreCase("winnings")) {
            winnings(nick, params);
        } else if (command.equalsIgnoreCase("winrate")) {
            winrate(nick, params);
        } else if (command.equalsIgnoreCase("rounds")) {
            rounds(nick, params);
        } else if (command.equalsIgnoreCase("player") || command.equalsIgnoreCase("p")){
            player(nick, params);
        } else if (command.equalsIgnoreCase("deposit")) {
            deposit(nick, params);
        } else if (command.equalsIgnoreCase("withdraw")) {
            withdraw(nick, params);
        } else if (command.equalsIgnoreCase("players")) {
            players(nick, params);
        } else if (command.equalsIgnoreCase("waitlist")) {
            waitlist(nick, params);
        } else if (command.equalsIgnoreCase("blacklist")) {
            blacklist(nick, params);
        } else if (command.equalsIgnoreCase("rank")) {
            rank(nick, params);
        } else if (command.equalsIgnoreCase("top")) {
            top(nick, params);
        } else if (command.equalsIgnoreCase("away")){
            away(nick, params);
        } else if (command.equalsIgnoreCase("back")){
            back(nick, params);
        } else if (command.equalsIgnoreCase("ping")) {
            ping(nick, params);
        } else if (command.equalsIgnoreCase("simple")) {
            simple(nick, params);
        } else if (command.equalsIgnoreCase("stats")){
            stats(nick, params);
        } else if (command.equalsIgnoreCase("grules") || command.equalsIgnoreCase("gamerules")) {
            grules(nick, params);
        } else if (command.equalsIgnoreCase("ghelp") || command.equalsIgnoreCase("gamehelp")) {
            ghelp(nick, params);
        } else if (command.equalsIgnoreCase("gcommands") || command.equalsIgnoreCase("gamecommands")) {
            gcommands(user, nick, params);
        } else if (command.equalsIgnoreCase("game")) {
            game(nick, params);
        /* Op commands */
        } else if (command.equalsIgnoreCase("fj") || command.equalsIgnoreCase("fjoin")){
            fjoin(user, nick, params);
        } else if (command.equalsIgnoreCase("fl") || command.equalsIgnoreCase("fq") || command.equalsIgnoreCase("fquit") || command.equalsIgnoreCase("fleave")){
            fleave(user, nick, params);
        } else if (command.equalsIgnoreCase("flast")) {
            flast(user, nick, params);
        } else if (command.equalsIgnoreCase("fstart") || command.equalsIgnoreCase("fgo")){
            fstart(user, nick, params);
        } else if (command.equalsIgnoreCase("fstop")){
            fstop(user, nick, params);
        } else if (command.equalsIgnoreCase("fb") || command.equalsIgnoreCase("fbet")){
            fbet(user, nick, params);
        } else if (command.equalsIgnoreCase("fa") || command.equalsIgnoreCase("fallin")){
            fallin(user, nick, params);
        } else if (command.equalsIgnoreCase("fr") || command.equalsIgnoreCase("fraise")){
            fraise(user, nick, params);
        } else if (command.equalsIgnoreCase("fc") || command.equalsIgnoreCase("fca") || command.equalsIgnoreCase("fcall")){
            fcall(user, nick, params);
        } else if (command.equalsIgnoreCase("fx") || command.equalsIgnoreCase("fch") || command.equalsIgnoreCase("fcheck")){
            fcheck(user, nick, params);
        } else if (command.equalsIgnoreCase("ff") || command.equalsIgnoreCase("ffold")){
            ffold(user, nick, params);
        } else if (command.equalsIgnoreCase("fdeposit")) {
            fdeposit(user, nick, params);
        } else if (command.equalsIgnoreCase("fwithdraw")) {
            fwithdraw(user, nick, params);
        } else if (command.equalsIgnoreCase("shuffle")){
            shuffle(user, nick, params);
        } else if (command.equalsIgnoreCase("cards")) {
            cards(user, nick, params);
        } else if (command.equalsIgnoreCase("discards")) {
            discards(user, nick, params);
        } else if (command.equalsIgnoreCase("settings")) {
            settings(user, nick, params);
        } else if (command.equalsIgnoreCase("set")){
            set(user, nick, params);
        } else if (command.equalsIgnoreCase("get")) {
            get(user, nick, params);
        } else if (command.equalsIgnoreCase("resetaway")){
            resetaway(user, nick, params);
        } else if (command.equalsIgnoreCase("resetsimple")) {
            resetsimple(user, nick, params);
        } else if (command.equalsIgnoreCase("reload")) {
            reload(user, nick, params);
        } else if (command.equalsIgnoreCase("trim")) {
            trim(user, nick, params);
        } else if (command.equalsIgnoreCase("test1")) {
            test1(user, nick, params);
        } else if (command.equalsIgnoreCase("test2")) {
            test2(user, nick, params);
        } else if (command.equalsIgnoreCase("test3")){
            test3(user, nick, params);
        }
    }

    /////////////////////////
    //// Command methods ////
    /////////////////////////
    
    /**
     * Starts a new round.
     * @param nick
     * @param params 
     */
    protected void start(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("round_started"));
        } else if (joined.size() < 2) {
            showMsg(getMsg("no_players"));
        } else if (startCount > 0) {
            informPlayer(nick, getMsg("no_manual_start"));
        } else {
            if (params.length > 0){
                try {
                    startCount = Math.min(get("autostarts") - 1, Integer.parseInt(params[0]) - 1);
                } catch (NumberFormatException e) {
                    // Do nothing and proceed
                }
            }
            state = PokerState.PRE_START;
            showStartRound();
            setStartRoundTask();
        }
    }
    
    /**
     * Sets a bet for the current player.
     * @param nick
     * @param params 
     */
    protected void bet(String nick, String[] params) {
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (findJoined(nick) != currentPlayer){
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));  
        } else {
            try {
                bet(Integer.parseInt(params[0]));
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Makes the current player call.
     * @param nick
     * @param params 
     */
    protected void call(String nick, String[] params) {
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (findJoined(nick) != currentPlayer){
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            call();
        }
    }
    
    /**
     * Makes the current player check.
     * @param nick
     * @param params 
     */
    protected void check(String nick, String[] params) {
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (findJoined(nick) != currentPlayer){
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            check();
        }
    }
    
    /**
     * Makes the current player fold.
     * @param nick
     * @param params 
     */
    protected void fold(String nick, String[] params) {
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (findJoined(nick) != currentPlayer){
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            fold();
        }
    }
    
    /**
     * Makes the current player raise.
     * @param nick
     * @param params 
     */
    protected void raise(String nick, String[] params) {
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (findJoined(nick) != currentPlayer){
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else {
            try {
                bet(Integer.parseInt(params[0]) + currentBet);
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Makes the current player go all in.
     * @param nick
     * @param params 
     */
    protected void allin(String nick, String[] params) {
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (findJoined(nick) != currentPlayer){
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            bet(currentPlayer.get("cash"));
        }
    }
    
    /**
     * Outputs the current community cards.
     * @param nick
     * @param params 
     */
    protected void community(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else {
            showCommunityCards(false);
        }
    }
    
    /**
     * Informs a player of his hand.
     * @param nick
     * @param params 
     */
    protected void hand(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else {
            PokerPlayer p = (PokerPlayer) findJoined(nick);
            informPlayer(nick, getMsg("tp_hand"), p.getHand());
        }
    }
    
    /**
     * Displays whose turn it is.
     * @param nick
     * @param params 
     */
    protected void turn(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else {
            showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), currentBet-currentPlayer.get("bet"), 
                    currentPlayer.get("bet"), currentBet, getCashInPlay(), currentPlayer.get("cash")-currentPlayer.get("bet"));
        }
    }
    
    /**
     * Displays the current players in the game.
     * @param nick
     * @param params 
     */
    protected void players(String nick, String[] params) {
        if (isInProgress()){
            showTablePlayers();
        } else {
            showMsg(getMsg("players"), getPlayerListString(joined));
        }
    }
    
    /**
     * Attempts to force the game to start.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fstart(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("round_started"));
        } else if (joined.size() < 2) {
            showMsg(getMsg("no_players"));
        } else {
            if (params.length > 0){
                try {
                    startCount = Math.min(get("autostarts") - 1, Integer.parseInt(params[0]) - 1);
                } catch (NumberFormatException e) {
                    // Do nothing and proceed
                }
            }
            state = PokerState.PRE_START;
            showStartRound();
            setStartRoundTask();
        }
    }
    
    /**
     * Forces a round to end. Use only as last resort. Data will be lost.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fstop(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else {
            cancelStartRoundTask();
            cancelIdleOutTask();
            for (int ctr = 0; ctr < joined.size(); ctr++) {
                resetPlayer((PokerPlayer) joined.get(ctr));
            }
            resetGame();
            startCount = 0;
            showMsg(getMsg("end_round"), getGameNameStr(), commandChar);
            state = PokerState.NONE;
            betState = PokerBet.NONE;
        }
    }
    
    /**
     * Forces the current player to bet.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fbet(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));       
        } else {
            try {
                bet(Integer.parseInt(params[0]));
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Forces the current player to go all in.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fallin(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            bet(currentPlayer.get("cash"));
        }
    }
    
    /**
     * Forces the current player to raise.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fraise(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));        
        } else {
            try {
                bet(Integer.parseInt(params[0]) + currentBet);
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }    
        }
    }
    
    /**
     * Forces the current player to call.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fcall(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            call();
        }
    }
    
    /**
     * Forces the current player to check.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fcheck(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            check();
        }
    }
    
    /**
     * Forces the current player to fold.
     * @param user
     * @param nick
     * @param params 
     */
    protected void ffold(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            fold();
        }
    }
    
    /**
     * Shuffles the deck.
     * @param user
     * @param nick
     * @param params 
     */
    protected void shuffle(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            shuffleDeck();
        }
    }
    
    /**
     * Reloads library files and settings.
     * @param user
     * @param nick
     * @param params 
     */
    protected void reload(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            awayList.clear();
            notSimpleList.clear();
            cmdMap.clear();
            opCmdMap.clear();
            aliasMap.clear();
            msgMap.clear();
            loadIni();
            loadHostList("away.txt", awayList);
            loadHostList("simple.txt", notSimpleList);
            loadStrLib(strFile);
            loadHelp(helpFile);
            showMsg(getMsg("reload"));
        }
    }
    
    /**
     * Performs test 1. Tests if the game will properly determine the winner 
     * with 2-5 players.
     * @param user
     * @param nick
     * @param params 
     */
    protected void test1(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (params.length < 1) {
            informPlayer(nick, getMsg("no_parameter"));  
        } else {
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
        }
    }
    
    /**
     * Performs test 2. Tests if arbitrary hands will be scored properly.
     * @param user
     * @param nick
     * @param params 
     */
    protected void test2(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else {
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
        }
    }
    
    /**
     * Performs test 3. Tests the percentage calculator for 2-5 players.
     * @param user
     * @param nick
     * @param params 
     */
    protected void test3(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else {
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
        }
    }
    
    /////////////////////////////////
    //// Game management methods ////
    /////////////////////////////////
    
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
            state = PokerState.BLINDS;
            betState = PokerBet.PRE_FLOP;
            setButton();
            setBlindBets();
            showTablePlayers();
            showButtonInfo();
            dealTable();
            continueRound();
        }
    }
    
    @Override
    public void continueRound() {
        state = PokerState.CONTINUE_ROUND;
        
        // Set currentPlayer if it hasn't been set yet
        if (currentPlayer == null) {
            if (betState.equals(PokerBet.PRE_FLOP)) {
                currentPlayer = bigBlind;
            } else {
                currentPlayer = dealer;
            }
        }
        
        /*
         * Find the next available player. If we reach the currentPlayer or 
         * topBettor then stop looking.
         */
        Player nextPlayer = getPlayerAfter(currentPlayer);
        while ((nextPlayer.has("fold") || nextPlayer.has("allin")) && 
                nextPlayer != currentPlayer && nextPlayer != topBettor) {
            nextPlayer = getPlayerAfter(nextPlayer);
        }
        
        if (getNumberNotFolded() < 2) {
            // If only one player hasn't folded, expedite to end of round
            addBetsToPot();
            currentPlayer = null;
            topBettor = null;
            
            // Deal some community cards
            while (!betState.equals(PokerBet.RIVER)) {
                burnCard();
                dealCommunity();
                betState = betState.next();
            }
            
            // Show final community if required
            if (settings.get("revealcommunity") == 1){
                showCommunityCards(true);
            }
            endRound();
        } else if (nextPlayer == topBettor || nextPlayer == currentPlayer) {
            // If we reach the firstPlayer or topBettor, then we have reached 
            // the end of a round of betting and we should deal community cards.
            minRaise = get("minbet");
            addBetsToPot();
            currentPlayer = null;
            topBettor = null;
            
            if (betState.equals(PokerBet.RIVER)){
                // If all community cards have been dealt, move to end of round.
                endRound();
            } else if (getNumberCanBet() < 2 && getNumberNotFolded() > 1) {
                /* 
                 * If showdown, show player hands and their win/tie 
                 * probabilities immediately and each time additional community
                 * cards are revealed. Adds a dramatic delay between each reveal.
                 */
                state = PokerState.SHOWDOWN;
                PokerSimulator sim;
                ArrayList<PokerPlayer> players = pots.get(0).getPlayers();

                while (!betState.equals(PokerBet.RIVER)) {
                    sim = new PokerSimulator(players, community);
                    String showdownStr = formatHeader(" Showdown: ") + " ";
                    for (PokerPlayer p : players) {
                        showdownStr += p.getNickStr() + " (" + p.getHand() + "||" + formatBold(Math.round(sim.getWinPct(p)) + "/" + Math.round(sim.getTiePct(p)) + "%%") + "), ";
                    }
                    showMsg(showdownStr.substring(0, showdownStr.length()-2));

                    // Add a delay for dramatic effect
                    try { Thread.sleep(get("showdown") * 1000); } catch (InterruptedException e){}

                    // Deal some community cards
                    burnCard();
                    dealCommunity();
                    betState = betState.next();
                    showCommunityCards(false);
                }
                endRound();
            } else {
                burnCard();
                dealCommunity();
                betState = betState.next();
                showCommunityCards(false);
                continueRound();
            }
        // Continue to the next bettor
        } else {
            state = PokerState.BETTING;
            currentPlayer = nextPlayer;
            showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), currentBet-currentPlayer.get("bet"), 
                        currentPlayer.get("bet"), currentBet, getCashInPlay(), currentPlayer.get("cash")-currentPlayer.get("bet"));
            setIdleOutTask();
        }
    }
    
    @Override
    public void endRound() {
        state = PokerState.END_ROUND;
        PokerPlayer p;

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
                            showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
                            ctr--;
                        }
                    // Give penalty to players with no cash in their bank
                    } else {
                        p.increment("bankrupts");
                        blacklist.add(p);
                        removeJoined(p);
                        showMsg(getMsg("unjoin_bankrupt"), p.getNickStr(), joined.size());
                        setRespawnTask(p);
                        ctr--;
                    }
                // Quitters
                } else if (p.has("quit")) {
                    removeJoined(p);
                    showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
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
        if (startCount > 0) {
            showMsg(getMsg("end_round_auto"), getGameNameStr(), commandChar);
        } else {
            showMsg(getMsg("end_round"), getGameNameStr(), commandChar);
        }
        mergeWaitlist();
        state = PokerState.NONE;
        betState = PokerBet.NONE;
        
        // Check if auto-starts remaining
        if (startCount > 0 && joined.size() > 1){
            startCount--;
            state = PokerState.PRE_START;
            showStartRound();
            setStartRoundTask();
        } else {
            startCount = 0;
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
        awayList.clear();
        notSimpleList.clear();
        cmdMap.clear();
        opCmdMap.clear();
        aliasMap.clear();
        msgMap.clear();
        settings.clear();
    }
    
    @Override
    public void resetGame() {
        currentBet = 0;
        minRaise = 0;
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
        PokerPlayer p = (PokerPlayer) findJoined(nick);
        
        switch (state){
            case NONE: case PRE_START:
                removeJoined(p);
                showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
                break;
            case BETTING:
                p.set("quit", 1);
                informPlayer(p.getNick(), getMsg("remove_end_round"));
                if (p == currentPlayer) {
                    fold();
                } else if (!p.has("fold")){
                    p.set("fold", 1);
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
                    if (getNumberNotFolded() == 1 && !state.equals(PokerState.CONTINUE_ROUND)){
                        call();
                    }
                }
                break;
            case BLINDS: case CONTINUE_ROUND:
                p.set("quit", 1);
                p.set("fold", 1);
                informPlayer(p.getNick(), getMsg("remove_end_round"));
                break;
            case SHOWDOWN: case END_ROUND:
                p.set("quit", 1);
                informPlayer(p.getNick(), getMsg("remove_end_round"));
                break;
            default:
                break;
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
        // dealer will never be set to null
        dealer = (PokerPlayer) getPlayerAfter(dealer);
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
        // Set the small blind
        if (get("minbet")/2 > smallBlind.get("cash")) {
            smallBlind.set("allin", 1);
            smallBlind.set("bet", smallBlind.get("cash"));
        } else {
            smallBlind.set("bet", get("minbet")/2);
        }
        
        // Set the big blind
        if (get("minbet") > bigBlind.get("cash")) {
            bigBlind.set("allin", 1);
            bigBlind.set("bet", bigBlind.get("cash"));
        } else {
            bigBlind.set("bet", get("minbet"));
        }
        
        // Set the current bet to minbet regardless of actual blinds
        currentBet = get("minbet");
        minRaise = get("minbet");
    }
    
    @Override
    public boolean isInProgress() {
        return !state.equals(PokerState.NONE);
    }
    
    //////////////////////////////////
    //// Game settings management ////
    //////////////////////////////////
    
    @Override
    protected void initSettings() {
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
        settings.put("ping", 600);
    }
    
    @Override
    protected void initCustom(){
        name = "texaspoker";
        helpFile = "texaspoker.help";
        deck = new CardDeck();
        deck.shuffleCards();
        pots = new ArrayList<PokerPot>();
        community = new Hand();
        house = new HouseStat();
        
        initSettings();
        loadHelp(helpFile);
        loadGameStats();
        loadIni();
        state = PokerState.NONE;
        betState = PokerBet.NONE;
        showMsg(getMsg("game_start"), getGameNameStr());
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
            out.println("#The rate-limit of the ping command");
            out.println("ping=" + get("ping"));
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
    
    /////////////////////////////////////////////////////////
    //// Card management methods for Texas Hold'em Poker ////
    /////////////////////////////////////////////////////////
    /**
     * Deals cards to the community hand.
     */
    protected void dealCommunity(){
        if (betState.equals(PokerBet.PRE_FLOP)) {
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
        if (!community.isEmpty()){
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
    
    //////////////////////////////////////////////
    //// Texas Hold'em Poker gameplay methods ////
    //////////////////////////////////////////////
    
    /**
     * Processes a bet command.
     * @param amount the amount to bet
     */
    public void bet(int amount) {
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
            p.set("bet", amount);
            continueRound();
        // A bet that's lower than the minimum raise
        } else if (amount - currentBet < minRaise){
            informPlayer(p.getNick(), getMsg("raise_too_low"), minRaise);
            setIdleOutTask();
        // A valid bet that's greater than the currentBet
        } else {
            p.set("bet", amount);
            topBettor = p;
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
        // A call or check
        } else {
            p.set("bet", total);
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
    
    ///////////////////////////////////
    //// Behind the scenes methods ////
    ///////////////////////////////////
    
    /**
     * Determines the number of players who have not folded.
     * @return the number of non-folded players
     */
    protected int getNumberNotFolded(){
        int numberNotFolded = 0;
        for (Player p : joined) {
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
        int numberCanBet = 0;
        for (Player p : joined) {
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
        int numberBettors = 0;
        for (Player p : joined) {
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
        
        // Add in the processed pots
        for (PokerPot pp : pots) {
            total += pp.getTotal();
        }
        
        // Add in the amounts currently being betted
        for (Player p : joined) {
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
            ArrayList<PlayerRecord> records = new ArrayList<PlayerRecord>();
            loadPlayerFile(records);
            int total = 0;
            
            for (PlayerRecord record : records) {
                if (record.has("tprounds")){
                    total++;
                }
            }
            return total;
        } catch (IOException e){
            manager.log("Error reading players.txt!");
            return 0;
        }
    }    
    
    ////////////////////////////////////////////////////////
    //// Message output methods for Texas Hold'em Poker ////
    ////////////////////////////////////////////////////////
    
    /**
     * Displays the players who are involved in a round. Players who have not
     * folded are displayed in bold. Designations for small blind, big blind,
     * and dealer are also shown.
     */
    public void showTablePlayers(){
        String msg = formatBold(joined.size()) + " players: ";
        String nickColor;
        for (Player p : joined) {
            // Give bold to remaining non-folded players
            nickColor = "";
            if (!p.has("fold")){
                nickColor = Colors.BOLD;
            }
            msg += nickColor + p.getNick();

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
            msg += nickColor + ", ";
        }
        showMsg(msg.substring(0, msg.length() - 2));
    }
    
    /**
     * Displays info on the dealer and blinds.
     */
    public void showButtonInfo() {
        showMsg(getMsg("tp_button_info"), dealer.getNickStr(false), smallBlind.getNickStr(false), minRaise/2, bigBlind.getNickStr(false), minRaise);
    }
    
    /**
     * Displays the community cards along with existing pots.
     * @param noTitle
     */
    public void showCommunityCards(boolean noTitle){
        String msg = "";
        
        // Append community cards to StringBuilder
        if (noTitle && betState.equals(PokerBet.RIVER)) {
            msg += formatHeader(" Community: ") + " " + community.toString() + " ";
        } else if (betState.equals(PokerBet.FLOP)) {
            msg += formatHeader(" Flop: ") + " " + community.toString() + " ";
        } else if (betState.equals(PokerBet.TURN)) {
            msg += formatHeader(" Turn: ") + " " + community.toString() + " ";
        } else if (betState.equals(PokerBet.RIVER)) {
            msg += formatHeader(" River: ") + " " + community.toString() + " ";
        } else {
            showMsg(getMsg("tp_no_community"));
            return;
        }
        
        // Append existing pots to StringBuilder
        for (int ctr = 0; ctr < pots.size(); ctr++){
            msg += Colors.YELLOW+",01 Pot #"+(ctr+1)+": "+Colors.GREEN+",01$"+formatNumber(pots.get(ctr).getTotal())+" "+Colors.NORMAL+" ";
        }
        
        // Append remaining non-folded players
        int notFolded = getNumberNotFolded();
        String pstr = "(" + formatBold(notFolded) + " players: ";
        for (Player p : joined) {
            if (!p.has("fold")){
                pstr += p.getNick(false) + ", ";
            }
        }
        msg += pstr.substring(0, pstr.length() - 2) + ")";
        
        showMsg(msg);
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
        if (winnings == Integer.MIN_VALUE) {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        } else {
            showMsg(getMsg("player_winnings"), formatNoPing(nick), winnings, getGameNameStr());
        }
    }
    
    @Override
    public void showPlayerWinRate(String nick){
        double winnings = (double) getPlayerStat(nick, "tpwinnings");
        double rounds = (double) getPlayerStat(nick, "tprounds");
        if (rounds == Integer.MIN_VALUE) {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        } else if (rounds == 0){
            showMsg(getMsg("player_no_rounds"), formatNoPing(nick), getGameNameStr());
        } else {
            showMsg(getMsg("player_winrate"), formatNoPing(nick), winnings/rounds, getGameNameStr());
        }    
    }
    
    @Override
    public void showPlayerRounds(String nick){
        int rounds = getPlayerStat(nick, "tprounds");
        if (rounds == Integer.MIN_VALUE) {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        } else if (rounds == 0){
            showMsg(getMsg("player_no_rounds"), formatNoPing(nick), getGameNameStr());
        } else {
            showMsg(getMsg("player_rounds"), formatNoPing(nick), rounds, getGameNameStr());
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
        if (cash == Integer.MIN_VALUE) {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        } else {
            showMsg(getMsg("player_all_stats"), formatNoPing(nick), cash, bank, net, bankrupts, winnings, rounds);
        }
    }

    @Override
    public void showPlayerRank(String nick, String stat){
        if (getPlayerStat(nick, "exists") != 1){
            showMsg(getMsg("no_data"), formatNoPing(nick));
            return;
        }
        
        try {
            PlayerRecord aRecord;
            ArrayList<PlayerRecord> records = new ArrayList<PlayerRecord>();
            loadPlayerFile(records);
            int length = records.size();
            String line = Colors.BLACK + ",08";
            
            if (stat.equalsIgnoreCase("winrate")) {
                int highIndex, rank = 0;
                ArrayList<String> nicks = new ArrayList<String>();
                ArrayList<Double> winrates = new ArrayList<Double>();
                
                for (int ctr = 0; ctr < length; ctr++) {
                    aRecord = records.get(ctr);
                    nicks.add(aRecord.getNick());
                    if (aRecord.get("tprounds") == 0){
                        winrates.add(0.);
                    } else {
                        winrates.add((double) aRecord.get("tpwinnings") / (double) aRecord.get("tprounds"));
                    }
                }
                
                line += "Texas Hold'em Win Rate: ";
                
                // Find the player with the highest value and check if it is 
                // the requested player. Repeat until found or end.
                for (int ctr = 0; ctr < length; ctr++){
                    highIndex = 0;
                    rank++;
                    for (int ctr2 = 0; ctr2 < nicks.size(); ctr2++) {
                        if (winrates.get(ctr2) > winrates.get(highIndex)) {
                            highIndex = ctr2;
                        }
                    }
                    
                    if (nick.equalsIgnoreCase(nicks.get(highIndex))){
                        line += "#" + rank + " " + Colors.WHITE + ",04 " + formatNoPing(nicks.get(highIndex)) + " $" + formatDecimal(winrates.get(highIndex)) + " ";
                        break;
                    } else {
                        nicks.remove(highIndex);
                        winrates.remove(highIndex);
                    }
                }
            } else {
                String statName = "";
                if (stat.equalsIgnoreCase("cash")) {
                    statName = "cash";
                    line += "Cash: ";
                } else if (stat.equalsIgnoreCase("bank")) {
                    statName = "bank";
                    line += "Bank: ";
                } else if (stat.equalsIgnoreCase("bankrupts")) {
                    statName = "bankrupts";
                    line += "Bankrupts: ";
                } else if (stat.equalsIgnoreCase("net") || stat.equals("netcash")) {
                    statName = "netcash";
                    line += "Net Cash: ";
                } else if (stat.equalsIgnoreCase("winnings")){
                    statName = "tpwinnings";
                    line += "Texas Hold'em Winnings: ";
                } else if (stat.equalsIgnoreCase("rounds")) {
                    statName = "tprounds";
                    line += "Texas Hold'em Rounds: ";
                } else {
                    throw new IllegalArgumentException();
                }
                
                // Sort based on stat
                Collections.sort(records, PlayerRecord.getComparator(statName));
                
                // Find the rank of the player
                for (int ctr = 0; ctr < length; ctr++){
                    aRecord = records.get(ctr);
                    if (nick.equalsIgnoreCase(aRecord.getNick())){
                        if (stat.equalsIgnoreCase("rounds") || stat.equalsIgnoreCase("bankrupts")) {
                            line += "#" + (ctr+1) + " " + Colors.WHITE + ",04 " + formatNoPing(aRecord.getNick()) + " " + formatNumber(aRecord.get(statName)) + " ";
                        } else {
                            line += "#" + (ctr+1) + " " + Colors.WHITE + ",04 " + formatNoPing(aRecord.getNick()) + " $" + formatNumber(aRecord.get(statName)) + " ";
                        }
                        break;
                    }
                }
            }
            
            // Show rank
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
        
        try {
            PlayerRecord aRecord;
            ArrayList<PlayerRecord> records = new ArrayList<PlayerRecord>();
            loadPlayerFile(records);
            int end = Math.min(n, records.size());
            int start = Math.max(end - 10, 0);
            String title = Colors.BOLD + Colors.BLACK + ",08 Top " + (start+1) + "-" + end;
            String list = Colors.BLACK + ",08";
            
            if (stat.equalsIgnoreCase("winrate")) {
                int highIndex;
                ArrayList<String> nicks = new ArrayList<String>();
                ArrayList<Double> winrates = new ArrayList<Double>();

                for (int ctr = 0; ctr < records.size(); ctr++) {
                    aRecord = records.get(ctr);
                    nicks.add(aRecord.getNick());
                    if (aRecord.get("tprounds") == 0){
                        winrates.add(0.);
                    } else {
                        winrates.add((double) aRecord.get("tpwinnings") / (double) aRecord.get("tprounds"));
                    }
                }
                
                title += " Texas Hold'em Win Rate ";
                
                // Find the player with the highest value, add to output string and remove.
                for (int ctr = 0; ctr < records.size(); ctr++){
                    highIndex = 0;
                    for (int ctr2 = 0; ctr2 < nicks.size(); ctr2++) {
                        if (winrates.get(ctr2) > winrates.get(highIndex)) {
                            highIndex = ctr2;
                        }
                    }
                    
                    // Only add those in the required range.
                    if (ctr >= start) {
                        list += " #" + (ctr+1) + ": " + Colors.WHITE + ",04 " + formatNoPing(nicks.get(highIndex)) + " $" + formatDecimal(winrates.get(highIndex)) + " " + Colors.BLACK + ",08";
                    }
                    
                    nicks.remove(highIndex);
                    winrates.remove(highIndex);
                    
                    // Break when we've reached the end of required range
                    if (ctr + 1 == end) {
                        break;
                    }
                }
            } else {
                String statName = "";
                if (stat.equalsIgnoreCase("cash")) {
                    statName = "cash";
                    title += " Cash ";
                } else if (stat.equalsIgnoreCase("bank")) {
                    statName = "bank";
                    title += " Bank ";
                } else if (stat.equalsIgnoreCase("bankrupts")) {
                    statName = "bankrupts";
                    title += " Bankrupts ";
                } else if (stat.equalsIgnoreCase("net") || stat.equalsIgnoreCase("netcash")) {
                    statName = "netcash";
                    title += " Net Cash ";
                } else if (stat.equalsIgnoreCase("winnings")){
                    statName = "tpwinnings";
                    title += " Texas Hold'em Winnings ";
                } else if (stat.equalsIgnoreCase("rounds")) {
                    statName = "tprounds";
                    title += " Texas Hold'em Rounds ";
                } else {
                    throw new IllegalArgumentException();
                }
                
                // Sort based on stat
                Collections.sort(records, PlayerRecord.getComparator(statName));

                // Add the players in the required range
                for (int ctr = start; ctr < end; ctr++){
                    aRecord = records.get(ctr);
                    if (stat.equalsIgnoreCase("rounds") || stat.equalsIgnoreCase("bankrupts")) {
                        list += " #" + (ctr+1) + ": " + Colors.WHITE + ",04 " + formatNoPing(aRecord.getNick()) + " " + formatNumber(aRecord.get(statName)) + " " + Colors.BLACK + ",08";
                    } else {
                        list += " #" + (ctr+1) + ": " + Colors.WHITE + ",04 " + formatNoPing(aRecord.getNick()) + " $" + formatNumber(aRecord.get(statName)) + " " + Colors.BLACK + ",08";
                    }
                }
            }
            
            // Output title and the list
            showMsg(title);
            showMsg(list);
        } catch (IOException e) {
            manager.log("Error reading players.txt!");
        }
    }
    
    /**
     * Displays the stack of each player in the given list in descending order.
     */
    public void showStacks() {
        ArrayList<Player> list = new ArrayList<Player>(joined);
        String msg = Colors.YELLOW + ",01 Stacks: " + Colors.NORMAL + " ";
        Collections.sort(list, Player.getComparator("cash"));
        
        for (Player p : list) {
            msg += p.getNick(false) + " (" + formatBold("$" + formatNumber(p.get("cash")));
            // Add player stack change
            if (p.get("change") > 0) {
                msg += "[" + Colors.DARK_GREEN + Colors.BOLD + "$" + formatNumber(p.get("change")) + Colors.NORMAL + "]";
            } else if (p.get("change") < 0) {
                msg += "[" + Colors.RED + Colors.BOLD + "$" + formatNumber(p.get("change")) + Colors.NORMAL + "]";
            } else {
                msg += "[" + Colors.BOLD + "$" + formatNumber(p.get("change")) + Colors.NORMAL + "]";
            }
            msg += "), ";
        }
        
        showMsg(msg.substring(0, msg.length()-2));
    }
    
    ///////////////////////////
    //// Formatted strings ////
    ///////////////////////////
    
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
