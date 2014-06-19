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

package irccasino.blackjack;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import org.pircbotx.*;
import irccasino.*;
import irccasino.cardgame.Card;
import irccasino.cardgame.CardDeck;
import irccasino.cardgame.CardGame;
import irccasino.cardgame.Hand;
import irccasino.cardgame.Player;
import irccasino.cardgame.Record;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Class for IRC Blackjack.
 * @author Yizhe Shen
 */
public class Blackjack extends CardGame {
    
    public enum BlackjackState {
        NONE, PRE_START, BETTING, PLAYING, CONTINUE_ROUND, END_ROUND
    }
    
    BlackjackPlayer dealer;
    IdleShuffleTask idleShuffleTask;
    // In-game properties
    protected BlackjackState state;
    protected boolean insuranceBets;
    protected int houseWinnings;

    public Blackjack() {
        super();
    }
    
    /**
     * The default constructor for Blackjack, subclass of CardGame.
     * This constructor loads the default INI file.
     * 
     * @param parent The bot that uses an instance of this class
     * @param commChar The command char
     * @param gameChannel The IRC channel in which the game is to be run.
     */
    public Blackjack(GameManager parent, char commChar, Channel gameChannel) {
        this(parent, commChar, gameChannel, "blackjack.ini");
    }
    
    /**
     * Allows a custom INI file to be loaded.
     * 
     * @param parent The bot that uses an instance of this class
     * @param commChar The command char
     * @param gameChannel The IRC channel in which the game is to be run
     * @param customINI the file path to a custom INI file
     */
    public Blackjack(GameManager parent, char commChar, Channel gameChannel, String customINI) {
        super(parent, commChar, gameChannel, customINI);
    }

    @Override
    public void processCommand(User user, String command, String[] params){
        String nick = user.getNick();
        String host = user.getHostmask();
        
        // Commands available in Blackjack.
        if (command.equalsIgnoreCase("join") || command.equalsIgnoreCase("j")){
            join(nick, host);
        } else if (command.equalsIgnoreCase("leave") || command.equalsIgnoreCase("quit") || command.equalsIgnoreCase("l") || command.equalsIgnoreCase("q")){
            leave(nick, params);
        } else if (command.equalsIgnoreCase("last")) {
            last(nick, params);
        } else if (command.equalsIgnoreCase("start") || command.equalsIgnoreCase("go")){
            start(nick, params);
        } else if (command.equalsIgnoreCase("stop")) {
            stop(nick, params);
        } else if (command.equalsIgnoreCase("bet") || command.equalsIgnoreCase("b")) {
            bet(nick, params);
        } else if (command.equalsIgnoreCase("allin") || command.equalsIgnoreCase("a")){
            allin(nick, params);
        } else if (command.equalsIgnoreCase("hit") || command.equalsIgnoreCase("h")) {
            hit(nick, params);
        } else if (command.equalsIgnoreCase("stand") || command.equalsIgnoreCase("stay") || command.equalsIgnoreCase("sit") || command.equalsIgnoreCase("s")) {
            stand(nick, params);
        } else if (command.equalsIgnoreCase("doubledown") || command.equalsIgnoreCase("dd")) {
            doubledown(nick, params);
        } else if (command.equalsIgnoreCase("surrender") || command.equalsIgnoreCase("surr")) {
            surrender(nick, params);
        } else if (command.equalsIgnoreCase("insure")) {
            insure(nick, params);
        } else if (command.equalsIgnoreCase("split")) {
            split(nick, params);
        } else if (command.equalsIgnoreCase("table")) {
            table(nick, params);
        } else if (command.equalsIgnoreCase("sum")) {
            sum(nick, params);
        } else if (command.equalsIgnoreCase("hand")) {
            hand(nick, params);
        } else if (command.equalsIgnoreCase("allhands")) {
            allhands(nick, params);
        } else if (command.equalsIgnoreCase("turn")) {
            turn(nick, params);
        } else if (command.equalsIgnoreCase("zc") || (command.equalsIgnoreCase("zen"))) {
            zen(nick, params);
        } else if (command.equalsIgnoreCase("hc") || (command.equalsIgnoreCase("hilo"))) {
            hilo(nick, params);
        } else if (command.equalsIgnoreCase("rc") || (command.equalsIgnoreCase("red7"))) {
            red7(nick, params);
        } else if (command.equalsIgnoreCase("count") || command.equalsIgnoreCase("c")){
            count(nick, params);
        } else if (command.equalsIgnoreCase("numcards") || command.equalsIgnoreCase("ncards")) {
            numcards(nick, params);
        } else if (command.equalsIgnoreCase("numdiscards") || command.equalsIgnoreCase("ndiscards")) {
            numdiscards(nick, params);
        } else if (command.equalsIgnoreCase("numdecks") || command.equalsIgnoreCase("ndecks")) {
            numdecks(nick, params);
        } else if (command.equalsIgnoreCase("players")) {
            players(nick, params);
        } else if (command.equalsIgnoreCase("house")) {
            house(nick, params);
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
        } else if (command.equalsIgnoreCase("rathole")) {
            rathole(nick, params);
        } else if (command.equalsIgnoreCase("withdraw")) {
            withdraw(nick, params);
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
        } else if (command.equalsIgnoreCase("fallin") || command.equalsIgnoreCase("fa")){
            fallin(user, nick, params);
        } else if (command.equalsIgnoreCase("fhit") || command.equalsIgnoreCase("fh")) {
            fhit(user, nick, params);
        } else if (command.equalsIgnoreCase("fstay") || command.equalsIgnoreCase("fstand") || command.equalsIgnoreCase("fsit")) {
            fstand(user, nick, params);
        } else if (command.equalsIgnoreCase("fdoubledown") || command.equalsIgnoreCase("fdd")) {
            fdoubledown(user, nick, params);
        } else if (command.equalsIgnoreCase("fsurrender") || command.equalsIgnoreCase("fsurr")) {
            fsurrender(user, nick, params);
        } else if (command.equalsIgnoreCase("fsplit")) {
            fsplit(user, nick, params);
        } else if (command.equalsIgnoreCase("finsure")) {
            finsure(user, nick, params);
        } else if (command.equalsIgnoreCase("fdeposit")) {
            fdeposit(user, nick, params);
        } else if (command.equalsIgnoreCase("fwithdraw")) {
            fwithdraw(user, nick, params);
        } else if (command.equalsIgnoreCase("shuffle")){
            shuffle(user, nick, params);
        } else if (command.equalsIgnoreCase("reload")){
            reload(user, nick, params);
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
        } else if (command.equalsIgnoreCase("trim")) {
            trim(user, nick, params);
        } else if (command.equalsIgnoreCase("query") || command.equalsIgnoreCase("sql")) {
            query(user, nick, params);
        } else if (command.equalsIgnoreCase("migrate")) {
            migrate(user, nick, params);
        } else if (command.equalsIgnoreCase("test1")){
            test1(user, nick, params);
        }
    }

    ///////////////////////////////////////
    //// Command methods for Blackjack ////
    ///////////////////////////////////////
    
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
        } else if (joined.size() < 1) {
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
            cancelIdleShuffleTask();
            state = BlackjackState.PRE_START;
            startTime = System.currentTimeMillis() / 1000;
            showStartRound();
            setStartRoundTask();
        }
    }
    
    /**
     * Makes a bet for the current player.
     * @param nick
     * @param params 
     */
    protected void bet(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.BETTING)) {
            informPlayer(nick, getMsg("no_betting"));
        } else if (currentPlayer != findJoined(nick)) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
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
     * Makes the current player go all in.
     * @param nick
     * @param params 
     */
    protected void allin(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.BETTING)) {
            informPlayer(nick, getMsg("no_betting"));
        } else if (currentPlayer != findJoined(nick)) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            bet(currentPlayer.getInteger("cash"));
        }
    }
    
    /**
     * Hits the current player with a card.
     * @param nick
     * @param params 
     */
    protected void hit(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else if (!(currentPlayer == findJoined(nick))) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            hit();
        }
    }
    
    /**
     * Lets the current player stand.
     * @param nick
     * @param params 
     */
    protected void stand(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else if (!(currentPlayer == findJoined(nick))) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            stay();
        }
    }
    
    /**
     * Lets the current player double down.
     * @param nick
     * @param params 
     */
    protected void doubledown(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else if (!(currentPlayer == findJoined(nick))) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            doubleDown();
        }
    }
    
    /**
     * Lets the current player surrender his hand.
     * @param nick
     * @param params 
     */
    protected void surrender(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else if (!(currentPlayer == findJoined(nick))) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            surrender();
        }
    }
    
    /**
     * Lets the current player insure his hand.
     * @param nick
     * @param params 
     */
    protected void insure(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else if (!(currentPlayer == findJoined(nick))) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else {
            try {
                insure(Integer.parseInt(params[0]));
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Lets the current player split his hand.
     * @param nick
     * @param params 
     */
    protected void split(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else if (!(currentPlayer == findJoined(nick))) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(BlackjackState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            split();
        }
    }
    
    /**
     * Displays all dealt hands.
     * @param nick
     * @param params 
     */
    protected void table(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            showTableHands(false);
        }
    }
    
    /**
     * Informs a player the sum of his current hand.
     * @param nick
     * @param params 
     */
    protected void sum(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            BlackjackPlayer p = (BlackjackPlayer) findJoined(nick);
            informPlayer(p.getNick(), getMsg("bj_hand_sum"), p.getHand().calcSum());
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
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            BlackjackPlayer p = (BlackjackPlayer) findJoined(nick);
            informPlayer(p.getNick(), getMsg("bj_hand"), p.getHand(), p.getHand().getBet());
        }
    }
    
    /**
     * Informs a player of all his hands.
     * @param nick
     * @param params 
     */
    protected void allhands(String nick, String[] params) {
        informPlayer(nick, "This command is not implemented.");
    }
    
    /**
     * Displays who the current player is.
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
            BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
            if (p.has("split")){
                showTurn(p, p.getInteger("currentindex") + 1);
            } else {
                showTurn(p, 0);
            }
        }
    }
    
    /**
     * Displays the current zen count.
     * @param nick
     * @param params 
     */
    protected void zen(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (!has("count")) {
            informPlayer(nick, getMsg("count_disabled"));
        } else {
            showMsg(getMsg("bj_zen"), getZen());
        }
    }
    
    /**
     * Displays the current hi-lo count.
     * @param nick
     * @param params 
     */
    protected void hilo(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (!has("count")) {
            informPlayer(nick, getMsg("count_disabled"));
        } else {
            showMsg(getMsg("bj_hilo"), getHiLo());
        }
    }
    
    /**
     * Displays the current red7 count.
     * @param nick
     * @param params 
     */
    protected void red7(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (!has("count")) {
            informPlayer(nick, getMsg("count_disabled"));
        } else {
            showMsg(getMsg("bj_red7"), getRed7());
        }
    }
    
    /**
     * Displays the current value of all counting methods.
     * @param nick
     * @param params 
     */
    protected void count(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (!has("count")) {
            informPlayer(nick, getMsg("count_disabled"));
        } else {
            showMsg(getMsg("bj_count"), deck.getNumberCards(), getHiLo(), getRed7(), getZen());
        }
    }
    
    /**
     * Displays the current number of cards in the dealer's shoe.
     * @param nick
     * @param params 
     */
    protected void numcards(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            showMsg(getMsg("bj_num_cards"), deck.getNumberCards());
        }
    }
    
    /**
     * Displays the current number of cards in the discard pile.
     * @param nick
     * @param params 
     */
    protected void numdiscards(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            showMsg(getMsg("num_discards"), deck.getNumberDiscards());
        }
    }
    
    /**
     * Displays the number of decks in the dealer's shoe.
     * @param nick
     * @param params 
     */
    protected void numdecks(String nick, String[] params) {
        showMsg(getMsg("num_decks"), getGameNameStr(), deck.getNumberDecks());
    }
    
    /**
     * Displays the players in the game.
     * @param nick
     * @param params 
     */
    protected void players(String nick, String[] params) {
        showMsg(getMsg("players"), getPlayerListString(joined));
    }
    
    /**
     * Displays the stats for the house.
     * @param nick
     * @param params 
     */
    protected void house(String nick, String[] params) {
        if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (params.length < 1){
            showHouseStat(get("decks"));
        } else {
            try {
                showHouseStat(Integer.parseInt(params[0]));
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Attempts to force start a round.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fstart(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("round_started"));
        } else if (joined.size() < 1) {
            showMsg(getMsg("no_players"));
        } else {
            if (params.length > 0){
                try {
                    startCount = Math.min(get("autostarts") - 1, Integer.parseInt(params[0]) - 1);
                } catch (NumberFormatException e) {
                    // Do nothing and proceed
                }
            }
            cancelIdleShuffleTask();
            state = BlackjackState.PRE_START;
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
        if (!channel.isOp(user)){
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()){
            informPlayer(nick, getMsg("no_start"));
        } else {
            cancelStartRoundTask();
            cancelIdleOutTask();
            for (Player p : joined) {
                resetPlayer(p);
            }
            resetGame();
            startCount = 0;
            showMsg(getMsg("end_round"), getGameNameStr(), commandChar);
            setIdleShuffleTask();
            state = BlackjackState.NONE;
        }
    }
    
    /**
     * Forces the current player to bet the specified amount.
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
        } else if (!state.equals(BlackjackState.BETTING)) {
            informPlayer(nick, getMsg("no_betting"));
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
        } else if (!state.equals(BlackjackState.BETTING)) {
            informPlayer(nick, getMsg("no_betting"));
        } else {
            bet(currentPlayer.getInteger("cash"));
        }
    }
    
    /**
     * Forces the current player to hit.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fhit(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            hit();
        }
    }
    
    /**
     * Forces the current player to stand.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fstand(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            stay();
        }
    }
    
    /**
     * Forces the current player to double down.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fdoubledown(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            doubleDown();
        }
    }
    
    /**
     * Forces the current player to surrender.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fsurrender(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            surrender();
        }
    }
    
    /**
     * Forces the current player to split his hand.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fsplit(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            split();
        }
    }
    
    /**
     * Forces the current player to insure his hand.
     * @param user
     * @param nick
     * @param params 
     */
    protected void finsure(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (!state.equals(BlackjackState.PLAYING)) {
            informPlayer(nick, getMsg("no_cards"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else {
            try {
                insure(Integer.parseInt(params[0]));
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Merges discards and shuffles the dealer's shoe.
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
            cancelIdleShuffleTask();
            shuffleShoe();
        }
    }
    
    /**
     * Reloads game settings and library files.
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
            cancelIdleShuffleTask();
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
     * Performs test 1. Tests the dealer playing algorithm and underlying 
     * calculations.
     * @param user
     * @param nick
     * @param params 
     */
    protected void test1(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            String outStr; 
            BlackjackHand h;
            showMsg("Dealing cards to Dealer...");
            // Deal cards to the dealer
            dealHand(dealer);
            h = dealer.getHand();
            showPlayerHand(dealer, h, 0, true);
            // Deal more cards if necessary
            while (h.calcSum() < 17 || (h.isSoft17() && has("soft17hit"))) {
                dealCard(h);
                showPlayerHand(dealer, h, 0, true);
            }
            // Output result
            if (h.isBlackjack()) {
                outStr = dealer.getNickStr() + " has blackjack (" + h.toString() + ").";
            } else {
                outStr = dealer.getNickStr() + " has " + h.calcSum() + " (" + h.toString() + ").";
            }
            showMsg(outStr);
            resetPlayer(dealer);
            showMsg(getMsg("separator"));
        }
    }
    
    ////////////////////////////////////////
    //// Game initialization management ////
    ////////////////////////////////////////
    
    @Override
    protected void set(String setting, int value) {
        super.set(setting, value);
        if (setting.equals("decks")) {
            cancelIdleShuffleTask();
            deck = new CardDeck(get("decks"));
            deck.shuffleCards();
            loadDBGameStats();
        }
    }
    
    @Override
    protected void initSettings() {
        // Do not use set()
        // Ini file settings
        settings.put("decks", 8);
        settings.put("cash", 1000);
        settings.put("idle", 60);
        settings.put("idlewarning", 45);
        settings.put("respawn", 600);
        settings.put("idleshuffle", 300);
        settings.put("count", 0);
        settings.put("hole", 0);
        settings.put("maxplayers", 15);
        settings.put("minbet", 5);
        settings.put("shufflepoint", 10);
        settings.put("soft17hit", 0);
        settings.put("autostarts", 10);
        settings.put("startwait", 5);
        settings.put("ping", 600);
    }
    
    @Override
    protected void initCustom() {
        name = "blackjack";
        helpFile = "blackjack.help";
        dealer = new BlackjackPlayer("Dealer");
        
        initSettings();
        loadHelp(helpFile);
        loadIni();
        state = BlackjackState.NONE;
        showMsg(getMsg("game_start"), getGameNameStr());
    }
    
    @Override
    protected final void loadIni() {
        super.loadIni();
        cancelIdleShuffleTask();
        deck = new CardDeck(get("decks"));
        deck.shuffleCards();
        loadDBGameStats();
    }
    
    @Override
    protected void saveIniFile() {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(iniFile)))) {
            out.println("#Settings");
            out.println("#Number of decks in the dealer's shoe");
            out.println("decks=" + get("decks"));
            out.println("#Number of seconds before a player idles out");
            out.println("idle=" + get("idle"));
            out.println("#Number of seconds before a player is given a warning for idling");
            out.println("idlewarning=" + get("idlewarning"));
            out.println("#Number of seconds of idleness after a round ends before the deck is shuffled");
            out.println("idleshuffle=" + get("idleshuffle"));
            out.println("#Initial amount given to new and bankrupt players");
            out.println("cash=" + get("cash"));
            out.println("#Number of seconds before a bankrupt player is allowed to join again");
            out.println("respawn=" + get("respawn"));
            out.println("#Whether card counting functions are enabled");
            out.println("count=" + get("count"));
            out.println("#Whether player hands are shown with a hole card in the main channel");
            out.println("hole=" + get("hole"));
            out.println("#The minimum bet required to see a hand");
            out.println("minbet=" + get("minbet"));
            out.println("#The number of cards remaining in the shoe when the discards are shuffled back");
            out.println("shufflepoint=" + get("shufflepoint"));
            out.println("#The maximum number of players allowed to join the game");
            out.println("maxplayers=" + get("maxplayers"));
            out.println("#Whether or not the dealer hits on soft 17");
            out.println("soft17hit=" + get("soft17hit"));
            out.println("#The maximum number of autostarts allowed");
            out.println("autostarts=" + get("autostarts"));
            out.println("#The wait time in seconds after the start command is given");
            out.println("startwait=" + get("startwait"));
            out.println("#The rate-limit of the ping command");
            out.println("ping=" + get("ping"));
        } catch (IOException e) {
            manager.log("Error creating " + iniFile + "!");
        }
    }

    /////////////////////////////////////////
    //// Player stats management methods ////
    /////////////////////////////////////////
    
    @Override
    protected Player loadDBPlayerRecord(String nick) {
        if (isBlacklisted(nick)) {
            return findBlacklisted(nick);
        } else if (isJoined(nick)) {
            return findJoined(nick);
        } else {
            BlackjackPlayer record = null;
            try (Connection conn = DriverManager.getConnection(dbURL)) {
                // Retrieve data from Player table if possible
                String sql = "SELECT id, nick, cash, bank, bankrupts, winnings, rounds, idles " +
                             "FROM Player INNER JOIN Purse INNER JOIN BJPlayerStat " +
                             "ON Player.id = Purse.player_id AND Player.id = BJPlayerStat.player_id " +
                             "WHERE nick = ? COLLATE NOCASE";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, nick);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.isBeforeFirst()) {
                            record = new BlackjackPlayer("");
                            record.put("id", rs.getInt("id"));
                            record.put("nick", rs.getString("nick"));
                            record.put("cash", rs.getInt("cash"));
                            record.put("bank", rs.getInt("bank"));
                            record.put("bankrupts", rs.getInt("bankrupts"));
                            record.put("winnings", rs.getInt("winnings"));
                            record.put("rounds", rs.getInt("rounds"));
                            record.put("idles", rs.getInt("idles"));
                        }
                    }
                }
                logDBWarning(conn.getWarnings());
            } catch (SQLException ex) {
                manager.log("SQL Error: " + ex.getMessage());
            }
            return record;
        }
    }
    
    @Override
    protected void loadDBPlayerData(Player p) {
        try (Connection conn = DriverManager.getConnection(dbURL)) {
            conn.setAutoCommit(false);
            
            // Initialize
            p.put("id", 0);
            p.put("cash", get("cash"));
            p.put("bank", 0);
            p.put("bankrupts", 0);
            p.put("rounds", 0);
            p.put("winnings", 0);
            p.put("idles", 0);
            
            // Retrieve data from Player table if possible
            String sql = "SELECT id FROM Player WHERE nick = ? COLLATE NOCASE";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, p.getNick());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.isBeforeFirst()) {
                        p.put("id", rs.getInt("id"));
                    }
                }
            }
            
            // Add new record if not found in Player table
            if (!p.has("id")) {
                sql = "INSERT INTO Player (nick, time_created) VALUES(?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, p.getNick());
                    ps.setLong(2, System.currentTimeMillis() / 1000);
                    ps.executeUpdate();
                    p.put("id", ps.getGeneratedKeys().getInt(1));
                }
            }
            
            // Retrieve data from Purse table if possible
            boolean found = false;
            sql = "SELECT cash, bank, bankrupts FROM Purse WHERE player_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, p.getInteger("id"));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.isBeforeFirst()) {
                        found = true;
                        p.put("cash", rs.getInt("cash"));
                        p.put("bank", rs.getInt("bank"));
                        p.put("bankrupts", rs.getInt("bankrupts"));
                    }
                }
            }
            
            // Add new record if not found in Purse
            if (!found) {
                informPlayer(p.getNick(), getMsg("new_player"), getGameNameStr(), get("cash"));
                sql = "INSERT INTO Purse (player_id, cash, bank, bankrupts) " +
                      "VALUES(?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, p.getInteger("id"));
                    ps.setInt(2, p.getInteger("cash"));
                    ps.setInt(3, p.getInteger("bank"));
                    ps.setInt(4, p.getInteger("bankrupts"));
                    ps.executeUpdate();
                }
            }
            
            // Retrieve data from BJPlayerStat table if possible
            found = false;
            sql = "SELECT rounds, winnings, idles " +
                  "FROM BJPlayerStat WHERE player_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, p.getInteger("id"));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.isBeforeFirst()) {
                        found = true;
                        p.put("rounds", rs.getInt("rounds"));
                        p.put("winnings", rs.getInt("winnings"));
                        p.put("idles", rs.getInt("idles"));
                    }
                }
            }
            
            // Add new record if not found in BJPlayerStat table
            if (!found) {
                sql = "INSERT INTO BJPlayerStat (player_id, rounds, winnings, idles) " +
                      "VALUES(?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, p.getInteger("id"));
                    ps.setInt(2, p.getInteger("rounds"));
                    ps.setInt(3, p.getInteger("winnings"));
                    ps.setInt(4, p.getInteger("idles"));
                    ps.executeUpdate();
                }
            }
            
            conn.commit();
            logDBWarning(conn.getWarnings());
        } catch (SQLException ex) {
            manager.log("SQL Error: " + ex.getMessage());
        }
    }
    
    @Override
    protected void saveDBPlayerDataBatch(ArrayList<Player> players) {
        try (Connection conn = DriverManager.getConnection(dbURL)) {
            conn.setAutoCommit(false);
            
            for (Player p : players) {
                // Update data in Purse table
                String sql = "UPDATE Purse SET cash = ?, bank = ?, bankrupts = ? " +
                             "WHERE player_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, p.getInteger("cash"));
                    ps.setInt(2, p.getInteger("bank"));
                    ps.setInt(3, p.getInteger("bankrupts"));
                    ps.setInt(4, p.getInteger("id"));
                    ps.executeUpdate();
                }

                // Update data in BJPlayerStat table
                sql = "UPDATE BJPlayerStat SET rounds = ?, winnings = ?, idles = ? " +
                      "WHERE player_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, p.getInteger("rounds"));
                    ps.setInt(2, p.getInteger("winnings"));
                    ps.setInt(3, p.getInteger("idles"));
                    ps.setInt(4, p.getInteger("id"));
                    ps.executeUpdate();
                }
            }
            
            conn.commit();
            logDBWarning(conn.getWarnings());
        } catch (SQLException ex) {
            manager.log("SQL Error: " + ex.getMessage());
        }
    }
    
    /////////////////////////////////////////////
    //// Game stats management for Blackjack ////
    /////////////////////////////////////////////
    
    /**
     * Returns the house statistics for a given shoe size.
     * @param numDecks shoe size in number of decks
     * @return the house stats
     */
    private Record loadDBHouse(int numDecks) {
        Record record = new Record();
        record.put("shoe_size", numDecks);
        record.put("rounds", 0);
        record.put("winnings", 0);
        
        try (Connection conn = DriverManager.getConnection(dbURL)) {
            conn.setAutoCommit(false);
            
            String sql = "SELECT rounds, winnings FROM BJHouse WHERE shoe_size = ?";
            boolean found = false;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, numDecks);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.isBeforeFirst()) {
                        found = true;
                        record.put("rounds", rs.getInt("rounds"));
                        record.put("winnings", rs.getInt("winnings"));
                    }
                }
            }
            
            if (!found) {
                sql = "INSERT INTO BJHouse (shoe_size, rounds, winnings) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, numDecks);
                    ps.setInt(2, 0);
                    ps.setInt(3, 0);
                    ps.executeUpdate();
                }
            }
            
            conn.commit();
            logDBWarning(conn.getWarnings());
        } catch (SQLException ex) {
            manager.log("SQL Error: " + ex.getMessage());
        }
        return record;
    }
    
    /**
     * Returns the total stats for the game.
     * @return a record containing queried stats
     */
    private Record loadDBGameTotals() {
        Record record = new Record();
        record.put("total_players", 0);
        record.put("total_rounds", 0);
        record.put("total_winnings", 0);
        
        try (Connection conn = DriverManager.getConnection(dbURL)) {
            String sql = "SELECT (SELECT COUNT(*) FROM BJPlayerStat WHERE rounds > 0) as total_players, " +
                             "(SELECT SUM(rounds) FROM BJHouse) AS total_rounds, " +
                             "(SELECT SUM(winnings) FROM BJHouse) AS total_winnings";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.isBeforeFirst()) {
                        record = new Record();
                        record.put("total_players", rs.getInt("total_players"));
                        record.put("total_rounds", rs.getInt("total_rounds"));
                        record.put("total_winnings", rs.getInt("total_winnings"));
                    }
                }
            }
            
            logDBWarning(conn.getWarnings());
        } catch (SQLException ex) {
            manager.log("SQL Error: " + ex.getMessage());
        }
        return record;
    }
    
    /**
     * Initializes house stats for the current shoe size.
     */
    protected void loadDBGameStats() {
        loadDBHouse(get("decks"));
    }
    
    @Override
    protected void saveDBGameStats() {
        int roundID, handID;
        
        try (Connection conn = DriverManager.getConnection(dbURL)) {
            conn.setAutoCommit(false);
            
            // Insert data into BJRound table
            String sql = "INSERT INTO BJRound (start_time, end_time, shoe_size, num_cards_left) " +
                         "VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, startTime);
                ps.setLong(2, endTime);
                ps.setInt(3, get("decks"));
                ps.setInt(4, deck.getNumberCards());
                ps.executeUpdate();
                roundID = ps.getGeneratedKeys().getInt(1);
            }
            
            for (Player p : joined) {
                // Insert data into BJPlayerChange table
                sql = "INSERT INTO BJPlayerChange (player_id, round_id, change, cash) " +
                      "VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, p.getInteger("id"));
                    ps.setInt(2, roundID);
                    ps.setInt(3, p.getInteger("change"));
                    ps.setInt(4, p.getInteger("cash"));
                    ps.executeUpdate();
                }
                
                if (p.getBoolean("idled")) {
                    // Insert data into BJPlayerIdle table
                    sql = "INSERT INTO BJPlayerIdle (player_id, round_id, idle_limit, idle_warning) " +
                          "VALUES (?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, p.getInteger("id"));
                        ps.setInt(2, roundID);
                        ps.setInt(3, get("idle"));
                        ps.setInt(4, get("idlewarning"));
                        ps.executeUpdate();
                    }
                }
                
                for (BlackjackHand h : ((BlackjackPlayer) p).getAllHands()) {
                    // Insert data into BJHand table
                    sql = "INSERT INTO BJHand (round_id, hand) VALUES (?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, roundID);
                        ps.setString(2, h.toStringDB());
                        ps.executeUpdate();
                        handID = ps.getGeneratedKeys().getInt(1);
                    }
                    
                    // Insert data into BJPlayerHand table
                    sql = "INSERT INTO BJPlayerHand (player_id, hand_id, bet, split, surrender, doubledown, result) " +
                          "VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, p.getInteger("id"));
                        ps.setInt(2, handID);
                        ps.setInt(3, h.getBet());
                        ps.setBoolean(4, ((BlackjackPlayer) p).has("split"));
                        ps.setBoolean(5, p.has("surrender"));
                        ps.setBoolean(6, p.has("doubledown"));
                        ps.setInt(7, h.compareTo(dealer.getHand()));
                        ps.executeUpdate();
                    }
                    
                    if (p.has("insurebet")) {
                        // Insert data into BJPlayerInsurance table
                        sql = "INSERT INTO BJPlayerInsurance (player_id, round_id, bet, result) " +
                              "VALUES (?, ?, ?, ?)";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setInt(1, p.getInteger("id"));
                            ps.setInt(2, roundID);
                            ps.setInt(3, p.getInteger("insurebet"));
                            ps.setBoolean(4, dealer.getHand().isBlackjack());
                            ps.executeUpdate();
                        }
                    }
                }
                
                // Insert Dealer's hand into BJHand table
                sql = "INSERT INTO BJHand (round_id, hand) VALUES (?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, roundID);
                    ps.setString(2, dealer.getHand().toStringDB());
                    ps.executeUpdate();
                }
            }
            
            // Update BJHouseStat table
            sql = "UPDATE BJHouse SET rounds=rounds+?, winnings=winnings+? WHERE shoe_size = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, 1);
                ps.setInt(2, houseWinnings);
                ps.setInt(3, get("decks"));
                ps.executeUpdate();
            }
            
            conn.commit();
            logDBWarning(conn.getWarnings());
        } catch (SQLException ex) {
            manager.log("SQL Error: " + ex.getMessage());
        }
    }
    
    ///////////////////////////////////////////////
    //// Game management methods for Blackjack ////
    ///////////////////////////////////////////////
    
    @Override
    public void addPlayer(String nick, String host) {
        addPlayer(new BlackjackPlayer(nick));
    }
    
    @Override
    public void addWaitlistPlayer(String nick, String host) {
        Player p = new BlackjackPlayer(nick);
        waitlist.add(p);
        informPlayer(p.getNick(), getMsg("join_waitlist"));
    }
    
    @Override
    public void leave(String nick) {
        BlackjackPlayer p = (BlackjackPlayer) findJoined(nick);

        switch (state) {
            case NONE: case PRE_START:
                removeJoined(p);
                showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
                break;
            case BETTING:
                if (p == currentPlayer){
                    cancelIdleOutTask();
                    currentPlayer = getNextPlayer();
                    removeJoined(p);
                    showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
                    if (currentPlayer == null) {
                        if (joined.isEmpty()) {
                            endRound();
                        } else {
                            dealTable();
                            currentPlayer = joined.get(0);
                            quickEval();
                        }
                    } else {
                        showTurn(currentPlayer, 0);
                        setIdleOutTask();
                    }
                } else {
                    if (p.has("initialbet")){
                        p.put("quit", true);
                        informPlayer(p.getNick(), getMsg("remove_end_round"));
                    } else {
                        removeJoined(p);
                        showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
                    }
                }
                break;
            case PLAYING:
                p.put("quit", true);
                informPlayer(p.getNick(), getMsg("remove_end_round"));
                if (p == currentPlayer){
                    stay();
                }
                break;
            case CONTINUE_ROUND: case END_ROUND:
                p.put("quit", true);
                informPlayer(p.getNick(), getMsg("remove_end_round"));
                break;
            default:
                break;
        }
    }
    
    @Override
    public void startRound() {
        if (joined.size() > 0) {
            state = BlackjackState.BETTING;
            showMsg(getMsg("players"), getPlayerListString(joined));
            currentPlayer = joined.get(0);
            showTurn(currentPlayer, 0);
            setIdleOutTask();
        } else {
            startCount = 0;
            endRound();
        }
    }
    
    @Override
    public void continueRound(){
        state = BlackjackState.CONTINUE_ROUND;
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        
        if (p.getInteger("currentindex") < p.getNumberHands() - 1) {
            p.getNextHand();
            quickEval();
        } else {
            currentPlayer = getNextPlayer();
            if (currentPlayer == null) {
                endRound();
            } else {
                quickEval();
            }
        }
    }
    
    @Override
    public void endRound() {
        state = BlackjackState.END_ROUND;

        if (joined.size() >= 1) {
            // Make dealer decisions
            if (needDealerPlay()) {
                showTurn(dealer, 0);
                BlackjackHand dHand = dealer.getHand();
                showPlayerHand(dealer, dHand, 0, true);
                while (dHand.calcSum() < 17 || (dHand.isSoft17() && has("soft17hit"))) {
                    // Add a 1 second delay for dramatic effect
                    try { Thread.sleep(1000); } catch (InterruptedException e){}
                    dealCard(dHand);
                    showPlayerHand(dealer, dHand, 0, true);
                }
                // Add a 1 second delay for dramatic effect
                try { Thread.sleep(1000); } catch (InterruptedException e){}
            }
            
            // Show results
            showResults();
            // Add a 1 second delay for dramatic effect
            try { Thread.sleep(1000); } catch (InterruptedException e){}
            if (insuranceBets) {
                showInsuranceResults();
            }
            
            /* Bookkeeping tasks
             * 1. Increment the number of rounds played for each player
             * 2. Increment idles if idled out
             * 2. Make auto-withdrawals
             * 3. Save player stats
             */
            for (Player p : joined) {
                p.add("rounds", 1);
                if (p.getBoolean("idled")) {
                    p.add("idles", 1);
                }
                if (!p.has("cash") && p.has("bank")) {
                    // Make a withdrawal if the player has a positive bankroll
                    int amount = Math.min(p.getInteger("bank"), get("cash"));
                    p.bankTransfer(-amount);
                    saveDBPlayerBanking(p);
                    informPlayer(p.getNick(), getMsg("auto_withdraw"), amount);
                }
            }
            
            // Save game stats
            endTime = System.currentTimeMillis() / 1000;
            saveDBPlayerDataBatch(joined);
            saveDBGameStats();
            
            /* Clean-up tasks
             * 1. Remove players who have gone bankrupt and set respawn timers
             * 2. Remove players who have quit or used the 'last' command
             * 3. Reset the players
             */
            for (int ctr = joined.size()-1; ctr >= 0; ctr--) {
                BlackjackPlayer p = (BlackjackPlayer) joined.get(ctr);
                if (!p.has("cash")) {
                    // Give penalty to players with no cash in their bankroll
                    p.add("bankrupts", 1);
                    blacklist.add(p);
                    removeJoined(p);
                    showMsg(getMsg("unjoin_bankrupt"), p.getNickStr(), joined.size());
                    setRespawnTask(p);
                } else if (p.has("quit") || p.has("last")) {
                    removeJoined(p.getNick());
                    showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
                }
                resetPlayer(p);
            }
        } else {
            showMsg(getMsg("no_players"));
        }
        
        resetGame();
        showMsg(getMsg("end_round"), getGameNameStr(), commandChar);
        mergeWaitlist();
        state = BlackjackState.NONE;
        
        // Check if any auto-starts remaining
        if (startCount > 0 && joined.size() > 0){
            startCount--;
            state = BlackjackState.PRE_START;
            showStartRound();
            setStartRoundTask();
        } else {
            startCount = 0;
            if (deck.getNumberDiscards() > 0) {
                setIdleShuffleTask();
            }
        }
    }
    
    @Override
    public void endGame() {
        cancelStartRoundTask();
        cancelIdleOutTask();
        cancelRespawnTasks();
        cancelIdleShuffleTask();
        gameTimer.cancel();
        deck = null;
        dealer = null;
        currentPlayer = null;
        devoiceAll();
        showMsg(getMsg("game_end"), getGameNameStr());
        awayList.clear();
        notSimpleList.clear();
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
        houseWinnings = 0;
        insuranceBets = false;
        discardPlayerHand(dealer);
        currentPlayer = null;
    }
    
    @Override
    protected void resetPlayer(Player p) {
        discardPlayerHand((BlackjackPlayer) p);
        p.clear("currentindex");
        p.clear("initialbet");
        p.clear("change");
        p.clear("last");
        p.clear("quit");
        p.clear("split");
        p.clear("surrender");
        p.clear("insurebet");
        p.clear("doubledown");
        p.clear("idled");
    }
    
    /**
     * Creates a new idle shuffle task.
     */
    public void setIdleShuffleTask() {
        idleShuffleTask = new IdleShuffleTask(this);
        gameTimer.schedule(idleShuffleTask, get("idleshuffle")*1000);
    }
    
    /**
     * Cancels the idle shuffle task if it exists.
     */
    public void cancelIdleShuffleTask() {
        if (idleShuffleTask != null){
            idleShuffleTask.cancel();
            gameTimer.purge();
        }
    }

    @Override
    public boolean isInProgress() {
        return !state.equals(BlackjackState.NONE);
    }
    
    ///////////////////////////////////////////////
    //// Card management methods for Blackjack ////
    ///////////////////////////////////////////////
    
    /**
     * Deals a card from the shoe to the specified hand.
     * @param h the hand
     */
    @Override
    public void dealCard(Hand h) {
        h.add(deck.takeCard());
        if (deck.getNumberCards() == get("shufflepoint")) {
            showMsg(getMsg("bj_deck_empty"));
            deck.refillDeck();
        }
    }
    
    /**
     * Merges the discards and shuffles the shoe.
     */
    public void shuffleShoe() {
        deck.refillDeck();
        showMsg(getMsg("bj_shuffle_shoe"));
    }
    
    /**
     * Deals two cards to the specified player.
     * @param p the player to be dealt to
     */
    private void dealHand(BlackjackPlayer p) {
        p.addHand();
        dealCard(p.getHand());
        dealCard(p.getHand());
    }
    
    /**
     * Deals hands (two cards) to everybody at the table.
     */
    public void dealTable() {
        BlackjackPlayer p;
        BlackjackHand h;
        for (int ctr = 0; ctr < joined.size(); ctr++) {
            p = (BlackjackPlayer) joined.get(ctr);
            dealHand(p);
            h = p.getHand();
            h.setBet(p.getInteger("initialbet"));
            // Send the player his hand in a hole game
            if (has("hole")) {
                informPlayer(p.getNick(), getMsg("bj_hand"), p.getHand(), p.getHand().getBet());
            }
        }
        dealHand(dealer);
        showTableHands(true);
    }
    
    /**
     * Discards a player's cards into the discard pile.
     * Loops through each hand that the player has.
     * @param p the player whose hands are to be discarded
     */
    private void discardPlayerHand(BlackjackPlayer p) {
        if (p.hasHands()) {
            for (int ctr = 0; ctr < p.getNumberHands(); ctr++) {
                deck.addToDiscard(p.getHand(ctr));
            }
            p.resetHands();
        }
    }

    ////////////////////////////////////
    //// Blackjack gameplay methods ////
    ////////////////////////////////////
    
    /**
     * Sets the initialize bet for the current player to see a hand.
     * The game then moves on to the next hand, player or phase.
     * 
     * @param amount the bet on the hand
     */
    private void bet(int amount) {
        cancelIdleOutTask();    
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        // Check if amount is greater than the player's stack
        if (amount > p.getInteger("cash")) {
            informPlayer(p.getNick(), getMsg("bet_too_high"), p.get("cash"));
            setIdleOutTask();
        // Check if the amount is less than minimum bet
        } else if (amount < get("minbet") && amount < p.getInteger("cash")) {
            informPlayer(p.getNick(), getMsg("bet_too_low"), get("minbet"));
            setIdleOutTask();
        } else {
            p.put("initialbet", amount);
            p.add("cash", -1 * amount);
            p.add("change", -1 * amount);
            p.add("winnings", -1 * amount);
            houseWinnings += amount;
            currentPlayer = getNextPlayer();
            if (currentPlayer == null) {
                dealTable();
                currentPlayer = joined.get(0);
                quickEval();
            } else {
                showTurn(currentPlayer, 0);
                setIdleOutTask();
            }
        }
    }
    
    /**
     * Lets the current Player stand.
     * The game then moves on to the next hand, player or phase.
     */
    private void stay() {
        cancelIdleOutTask();
        continueRound();
    }
    
    /**
     * Gives the current Player's hand an additional card.
     * Checks if the hand is now bust.
     */
    private void hit() {
        cancelIdleOutTask();
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        BlackjackHand h = p.getHand();
        dealCard(h);
        showHitResult(p,h);
        if (h.isBust()) {
            continueRound();
        } else {
            setIdleOutTask();
        }
    }
    
    /**
     * Gives the current Player's hand an additional card and doubles the bet
     * on the hand. The game then moves on to the next hand, player or phase.
     */
    private void doubleDown() {
        cancelIdleOutTask();
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        BlackjackHand h = p.getHand();
        if (h.hasHit()) {
            informPlayer(p.getNick(), getMsg("no_dd"));
            setIdleOutTask();
        } else if (p.getInteger("initialbet") > p.getInteger("cash")) {
            informPlayer(p.getNick(), getMsg("insufficient_funds"));
            setIdleOutTask();
        } else {			
            p.add("cash", -1 * h.getBet());
            p.add("change", -1 * h.getBet());
            p.add("winnings", -1 * h.getBet());
            houseWinnings += h.getBet();
            h.addBet(h.getBet());
            p.put("doubledown", true);
            showMsg(getMsg("bj_dd"), p.getNickStr(false), h.getBet(), p.get("cash"));
            dealCard(h);
            showHitResult(p,h);
            continueRound();
        }
    }
    
    /**
     * Lets the current Player surrender his hand and receive back half the 
     * bet on that hand. The game then moves on to the hand, player or phase.
     */
    private void surrender() {
        cancelIdleOutTask();
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        BlackjackHand h = p.getHand();
        if (p.has("split")){
            informPlayer(p.getNick(), getMsg("no_surr_split"));
            setIdleOutTask();
        } else if (h.hasHit()) {
            informPlayer(p.getNick(), getMsg("no_surr"));
            setIdleOutTask();
        } else {
            p.add("cash", calcHalf(p.getInteger("initialbet")));
            p.add("change", calcHalf(p.getInteger("initialbet")));
            p.add("winnings", calcHalf(p.getInteger("initialbet")));
            houseWinnings -= calcHalf(p.getInteger("initialbet"));
            p.put("surrender", true);
            showMsg(getMsg("bj_surr"), p.getNickStr(false), p.get("cash"));
            continueRound();
        }
    }
    
    /**
     * Sets the insurance bet for the current Player.
     * 
     * @param amount the insurance bet
     */
    private void insure(int amount) {
        cancelIdleOutTask();
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        BlackjackHand h = p.getHand();
        if (p.has("insurebet")) {
            informPlayer(p.getNick(), getMsg("already_insured"));
        } else if (!dealerUpcardAce()) {
            informPlayer(p.getNick(), getMsg("no_insure_no_ace"));
        } else if (h.hasHit()) {
            informPlayer(p.getNick(), getMsg("no_insure_has_hit"));
        } else if (p.has("split")){
            informPlayer(p.getNick(), getMsg("no_insure_has_split"));
        } else if (amount > p.getInteger("cash")) {
            informPlayer(p.getNick(), getMsg("insufficient_funds"));
        } else if (amount > calcHalf(p.getInteger("initialbet"))) {
            informPlayer(p.getNick(), getMsg("insure_bet_too_high"), calcHalf(p.getInteger("initialbet")));
        } else if (amount <= 0) {
            informPlayer(p.getNick(), getMsg("insure_bet_too_low"));
        } else {
            insuranceBets = true;
            p.put("insurebet", amount);
            p.add("cash", -1 * amount);
            p.add("change", -1 * amount);
            p.add("winnings", -1 * amount);
            houseWinnings += amount;
            showMsg(getMsg("bj_insure"), p.getNickStr(false), p.get("insurebet"), p.get("cash"));
        }
        setIdleOutTask();
    }
    
    /**
     * Lets the current Player split the current hand into two hands, each
     * with its own bet.
     */
    private void split() {
        cancelIdleOutTask();
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        BlackjackHand nHand, cHand = p.getHand();
        if (!cHand.isPair()) {
            informPlayer(p.getNick(), getMsg("no_pair"));
            setIdleOutTask();
        } else if (p.getInteger("cash") < cHand.getBet()) {
            informPlayer(p.getNick(), getMsg("insufficient_funds"));
            setIdleOutTask();
        } else {
            p.add("cash", -1 * cHand.getBet());
            p.add("change", -1 * cHand.getBet());
            p.add("winnings", -1 * cHand.getBet());
            houseWinnings += cHand.getBet();
            p.splitHand();
            dealCard(cHand);
            nHand = p.getHand(p.getInteger("currentindex") + 1);
            dealCard(nHand);
            nHand.setBet(cHand.getBet());
            showSplitHands(p);
            showMsg(getMsg("separator"));
            quickEval();
        }
    }

    /////////////////////////////////////////////
    //// Blackjack behind-the-scenes methods ////
    /////////////////////////////////////////////
    
    /**
     * Determines what to do when the action falls to a new player/hand
     */
    private void quickEval() {
        state = BlackjackState.PLAYING;
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        
        if (p.has("quit")){
            stay();
        } else {
            if (p.has("split")) {
                showTurn(p, p.getInteger("currentindex") + 1);
            } else {
                showTurn(p, 0);
            }
            setIdleOutTask();
        }
    }
    
    /**
     * Calculates half of an amount rounded up.
     * @param amount
     * @return half of the amount rounded up
     */
    private int calcHalf(int amount) {
        return (int) (Math.ceil((double) (amount) / 2.));
    }
    
    /**
     * Calculates the winnings for a Blackjack win.
     * @param h a hand with Blackjack
     * @return the payout
     */
    private int calcBlackjackPayout(BlackjackHand h){
        return (2 * h.getBet() + calcHalf(h.getBet()));
    }
    
    /**
     * Calculates the winnings for a regular win.
     * @param h a winning hand
     * @return the payout
     */
    private int calcWinPayout(BlackjackHand h){
        return 2 * h.getBet();
    }
    
    /**
     * Calculates the winnings for an insurance win.
     * @param p a player with an insurance bet
     * @return the payout
     */
    private int calcInsurancePayout(BlackjackPlayer p){
        return 3 * p.getInteger("insurebet");
    }
    
    /**
     * Determines if the dealer's upcard is an Ace.
     * @return true if it is an Ace
     */
    private boolean dealerUpcardAce() {
        return dealer.getHand().get(1).isFace("A");
    }
    
    /**
     * Determines if the dealer needs to play his hand.
     * If all the players have busted, surrendered or Blackjack then the
     * dealer does not need to play his hand.
     * @return true if one player does not meet the requirements.
     */
    private boolean needDealerPlay() {
        for (int ctr = 0; ctr < joined.size(); ctr++) {
            BlackjackPlayer p = (BlackjackPlayer) joined.get(ctr);
            for (int ctr2 = 0; ctr2 < p.getNumberHands(); ctr2++) {
                BlackjackHand h = p.getHand(ctr2);
                if (!h.isBust() && !p.has("surrender") && !h.isBlackjack()) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Pays winnings.
     * @param p the player
     * @param h the hand to calculate
     */
    private void payPlayer(BlackjackPlayer p, BlackjackHand h){
        int result = h.compareTo(dealer.getHand());
        int payout = 0;
        switch (result){
            case 2: payout = calcBlackjackPayout(h); break;
            case 1: payout = calcWinPayout(h); break;
            case 0: payout = h.getBet(); break;
            default:
        }
        p.add("cash", payout);
        p.add("change", payout);
        p.add("winnings", payout);
        houseWinnings -= payout;
    }
    
    /**
     * Pays insurance winnings.
     * @param p the player with an insurance bet
     */
    private void payPlayerInsurance(BlackjackPlayer p){
        if (dealer.getHand().isBlackjack()) {
            p.add("cash", calcInsurancePayout(p));
            p.add("change", calcInsurancePayout(p));
            p.add("winnings", calcInsurancePayout(p));
            houseWinnings -= calcInsurancePayout(p);
        }
    }

    ///////////////////////////////
    //// Card-counting methods ////
    ///////////////////////////////
    
    /**
     * Calculates the Zen count.
     * Contributors: Yky, brrr 
     */
    private int getZen() {
        int zenCount = 0;
        String face;
        for (Card discard : deck.getDiscards()) {
            face = discard.getFace();
            if (new StringTokenizer(face, "23").countTokens() == 0) {
                zenCount++;
            } else if (new StringTokenizer(face, "456").countTokens() == 0) {
                zenCount += 2;
            } else if (face.equals("7")) {
                zenCount++;
            } else if (new StringTokenizer(face, "TJQK").countTokens() == 0) {
                zenCount -= 2;
            } else if (face.equals("A")) {
                zenCount--;
            }
        }
        return zenCount;
    }
    
    /**
     * Calculates the hi-lo count.
     * Contributors: Yky, brrr 
     */
    private int getHiLo() {
        int hiLo = 0;
        String face;
        for (Card discard : deck.getDiscards()) {
            face = discard.getFace();
            if (new StringTokenizer(face, "23456").countTokens() == 0) {
                hiLo++;
            } else if (new StringTokenizer(face, "TJQKA").countTokens() == 0) {
                hiLo--;
            }
        }
        return hiLo;
    }
    
    /**
     * Calculates the red 7 count.
     * Contributors: Yky, brrr
     */
    private double getRed7() {
        double red7 = -2 * get("decks");
        String face;
        for (Card discard : deck.getDiscards()) {
            face = discard.getFace();
            if (new StringTokenizer(face, "23456").countTokens() == 0) {
                red7++;
            } else if (new StringTokenizer(face, "TJQKA").countTokens() == 0) {
                red7--;
            } else if (face.equals("7")) {
                red7 += 0.5;
            }
        }
        return red7;
    }
    
    //////////////////////////////////////////////
    //// Message output methods for Blackjack ////
    //////////////////////////////////////////////
    
    /**
     * Shows house stats for a given shoe size.
     * @param n the number of decks in the shoe
     */
    public void showHouseStat(int n) {
        Record record = loadDBHouse(n);
        if (record != null) {
            showMsg(getMsg("bj_house_str"), record.get("rounds"), record.get("shoe_size"), record.get("winnings"));
        } else {
            showMsg(getMsg("bj_no_stats"), n);
        }
    }
    
    /**
     * Displays which player is currently required to act.
     * @param p the player required to act
     * @param index the index of the hand
     */
    public void showTurn(Player p, int index) {
        if (state.equals(BlackjackState.BETTING)) {
            showMsg(getMsg("bj_turn_betting"), p.getNickStr(), p.get("cash"), p.get("cash"));
        } else if (index == 0) {
            showMsg(getMsg("bj_turn"), p.getNickStr());
        } else {
            showMsg(getMsg("bj_turn_split"), p.getNickStr(), index);
        }
    }
    
    /**
     * Displays a player's hand.
     * @param p the player
     * @param h the player's hand
     * @param index the index of the hand
     * @param forceNoHole whether to force reveal hole card
     */
    private void showPlayerHand(BlackjackPlayer p, BlackjackHand h, int index, boolean forceNoHole) {
        if (index == 0) {
            if (forceNoHole){
                if (h.isBlackjack()) {
                    showMsg(getMsg("bj_show_hand_bj"), p.getNickStr(), h);
                } else if (h.isBust()) {
                    showMsg(getMsg("bj_show_hand_bust"), p.getNickStr(), h);
                } else {
                    showMsg(getMsg("bj_show_hand"), p.getNickStr(), h);
                }
            } else if (has("hole") || p == dealer) {
                if (h.isBust()) {
                    showMsg(getMsg("bj_show_hand_bust"), p.getNickStr(), h.toString(1));
                } else {
                    showMsg(getMsg("bj_show_hand"), p.getNickStr(), h.toString(1));
                }
            } else {
                if (h.isBlackjack()) {
                    showMsg(getMsg("bj_show_hand_bj"), p.getNickStr(), h);
                } else if (h.isBust()) {
                    showMsg(getMsg("bj_show_hand_bust"), p.getNickStr(), h);
                } else {
                    showMsg(getMsg("bj_show_hand"), p.getNickStr(), h);
                }
            }
        } else {
            if (has("hole")) {
                if (h.isBust()) {
                    showMsg(getMsg("bj_show_split_hand_bust"), p.getNickStr(), index, h.toString(1));
                } else {
                    showMsg(getMsg("bj_show_split_hand"), p.getNickStr(), index, h.toString(1));
                }
            } else {
                if (h.isBlackjack()) {
                    showMsg(getMsg("bj_show_split_hand_bj"), p.getNickStr(), index, h);
                } else if (h.isBust()) {
                    showMsg(getMsg("bj_show_split_hand_bust"), p.getNickStr(), index, h);
                } else {
                    showMsg(getMsg("bj_show_split_hand"), p.getNickStr(), index, h);
                }
            }
        }
    }
    
    /**
     * Method to display split hands after a split.
     * @param p the player
     * @param h the hand
     * @param index the index of the hand
     */
    private void showPlayerHandWithBet(BlackjackPlayer p, BlackjackHand h, int index) {
        if (has("hole")) {
            showMsg(getMsg("bj_show_split_hand_bet"), p.getNickStr(), index, h.toString(1), h.getBet());
        } else {
            showMsg(getMsg("bj_show_split_hand_bet"), p.getNickStr(), index, h, h.getBet());
        }
    }
    
    /**
     * Method to display all of a player's split hands after a split.
     * @param p the player
     */
    private void showSplitHands(BlackjackPlayer p) {
        BlackjackHand h;
        showMsg(getMsg("bj_split"), p.getNickStr(false), p.getNickStr(false));
        for (int ctr = 0; ctr < p.getNumberHands(); ctr++) {
            h = p.getHand(ctr);
            showPlayerHandWithBet(p, h, ctr + 1);
        }
        showMsg(getMsg("bj_stack"), p.getNickStr(), p.get("cash"));
    }
    
    /**
     * Shows the result of a hit or double-down.
     * @param p the player
     * @param h the player's hand
     */
    private void showHitResult(BlackjackPlayer p, BlackjackHand h){
        if (p.has("split")) {
            showPlayerHand(p, h, p.getInteger("currentindex") + 1, false);
        } else {
            showPlayerHand(p, h, 0, false);
        }
    }

    /**
     * Displays the dealt hands of the players and the dealer.
     * @param dealing
     */
    public void showTableHands(boolean dealing) {
        BlackjackPlayer p;
        if (dealing){
            showMsg(formatHeader(" Dealing Table... "));
        } else {
            showMsg(formatHeader(" Table: "));
        }
        for (int ctr = 0; ctr < joined.size(); ctr++) {
            p = (BlackjackPlayer) joined.get(ctr);
            for (int ctr2 = 0; ctr2 < p.getNumberHands(); ctr2++){
                if (p.has("split")) {
                    showPlayerHand(p, p.getHand(ctr2), ctr2+1, false);
                } else {
                    showPlayerHand(p, p.getHand(ctr2), 0, false);
                }
            }
        }
        showPlayerHand(dealer, dealer.getHand(), 0, false);
    }
    
    /**
     * Displays the final results of the round.
     */
    public void showResults() {
        BlackjackPlayer p;
        BlackjackHand h;
        showMsg(formatHeader(" Results: "));
        showDealerResult();
        for (int ctr = 0; ctr < joined.size(); ctr++) {
            p = (BlackjackPlayer) joined.get(ctr);
            for (int ctr2 = 0; ctr2 < p.getNumberHands(); ctr2++) {
                h = p.getHand(ctr2);
                if (!p.has("surrender")){
                    payPlayer(p,h);
                }
                if (p.has("split")) {
                    showPlayerResult(p, h, ctr2+1);
                } else {
                    showPlayerResult(p, h, 0);
                }
            }
        }
    }
    
    /**
     * Displays the results of any insurance bets.
     */
    public void showInsuranceResults() {
        BlackjackPlayer p;
        showMsg(formatHeader(" Insurance Results: "));
        if (dealer.getHand().isBlackjack()) {
            showMsg(dealer.getNickStr() + " had blackjack.");
        } else {
            showMsg(dealer.getNickStr() + " did not have blackjack.");
        }

        for (int ctr = 0; ctr < joined.size(); ctr++) {
            p = (BlackjackPlayer) joined.get(ctr);
            if (p.has("insurebet")) {
                payPlayerInsurance(p);
                showPlayerInsuranceResult(p);
            }
        }
    }
    
    /**
     * Displays the result of the dealer's hand.
     */
    public void showDealerResult() {
        BlackjackHand dHand = dealer.getHand();
        if (dHand.isBlackjack()) {
            showMsg(getMsg("bj_dealer_result_bj"), dealer.getNickStr(), dHand);
        } else {
            showMsg(getMsg("bj_dealer_result"), dealer.getNickStr(), dHand.calcSum(), dHand);
        }
    }
    
    /**
     * Outputs the result of a player's hand to the game channel.
     * @param p the player to show
     * @param h the player's hand of which the results are to be shown
     * @param index the hand index if the player has split
     */
    private void showPlayerResult(BlackjackPlayer p, BlackjackHand h, int index) {
        String nickStr;
        if (index > 0){
            nickStr = p.getNickStr() + "-" + index;
        } else {
            nickStr = p.getNickStr();
        }
        int result = h.compareTo(dealer.getHand());
        if (p.has("surrender")) {
            showMsg(getMsg("bj_result_surr"), getSurrStr(), nickStr, h.calcSum(), h, p.get("cash"));
        } else {
            switch (result) {
                case 2: // Blackjack win
                    showMsg(getMsg("bj_result_bj"), getWinStr(), nickStr, h, calcBlackjackPayout(h), p.get("cash"));
                    break;
                case 1: // Regular win
                    showMsg(getMsg("bj_result_win"), getWinStr(), nickStr, h.calcSum(), h, calcWinPayout(h), p.get("cash"));
                    break;
                case 0: // Push
                    showMsg(getMsg("bj_result_push"), getPushStr(), nickStr, h.calcSum(), h, h.getBet(), p.get("cash"));
                    break;
                default: // Loss
                    showMsg(getMsg("bj_result_loss"), getLossStr(), nickStr, h.calcSum(), h, p.get("cash"));
            }
        }
    }
    
    /**
     * Displays the result of a player's insurance bet.
     * @param p a player who has made an insurance bet
     */
    private void showPlayerInsuranceResult(BlackjackPlayer p) {
        if (dealer.getHand().isBlackjack()) {
            showMsg(getMsg("bj_insure_win"), getWinStr(), p.getNickStr(), calcInsurancePayout(p), p.get("cash"));
        } else {
            showMsg(getMsg("bj_insure_loss"), getLossStr(), p.getNickStr(), p.get("cash"));
        }
    }
    
    @Override
    public void showPlayerRank(String nick, String stat) throws IllegalArgumentException {
        String statName = "";
        String line = Colors.BLACK + ",08";
        String sql;
        
        // Build SQL query
        if (stat.equals("cash")) {
            sql = "SELECT nick, cash, " +
                      "(SELECT COUNT(*) FROM Purse WHERE cash > t1.cash)+1 AS rank " +
                  "FROM (Player INNER JOIN Purse ON Player.id = Purse.player_id) AS t1 " +
                  "WHERE nick = ? COLLATE NOCASE";
            statName = "cash";
            line += "Cash: ";
        } else if (stat.equalsIgnoreCase("bank")) {
            sql = "SELECT nick, bank, " +
                      "(SELECT COUNT(*) FROM Purse WHERE bank > t1.bank)+1 AS rank " +
                  "FROM (Player INNER JOIN Purse ON Player.id = Purse.player_id) AS t1 " +
                  "WHERE nick = ? COLLATE NOCASE";
            statName = "bank";
            line += "Bank: ";
        } else if (stat.equalsIgnoreCase("bankrupts")) {
            sql = "SELECT nick, bankrupts, " +
                      "(SELECT COUNT(*) FROM Purse WHERE bankrupts > t1.bankrupts)+1 AS rank " +
                  "FROM (Player INNER JOIN Purse ON Player.id = Purse.player_id) AS t1 " +
                  "WHERE nick = ? COLLATE NOCASE";
            statName = "bankrupts";
            line += "Bankrupts: ";
        } else if (stat.equalsIgnoreCase("net") || stat.equals("netcash")) {
            sql = "SELECT nick, cash+bank AS netcash, " +
                      "(SELECT COUNT(*) FROM Purse WHERE cash+bank > t1.cash+t1.bank)+1 AS rank " +
                  "FROM (Player INNER JOIN Purse ON Player.id = Purse.player_id) AS t1 " +
                  "WHERE nick = ? COLLATE NOCASE";
            statName = "netcash";
            line += "Net Cash: ";
        } else if (stat.equalsIgnoreCase("rounds")) {
            sql = "SELECT nick, rounds, " +
                      "(SELECT COUNT(*) FROM BJPlayerStat WHERE rounds > 0 AND rounds > t1.rounds)+1 AS rank " +
                  "FROM (Player INNER JOIN BJPlayerStat ON Player.id = BJPlayerStat.player_id) AS t1 " +
                  "WHERE nick = ? COLLATE NOCASE";
            statName = "rounds";
            line += "Blackjack Rounds (min. 1 round): ";
        } else if (stat.equalsIgnoreCase("winnings")) {
            sql = "SELECT nick, rounds, winnings, " +
                      "(SELECT COUNT(*) FROM BJPlayerStat WHERE rounds > 0 AND winnings > t1.winnings)+1 AS rank " +
                  "FROM (Player INNER JOIN BJPlayerStat ON Player.id = BJPlayerStat.player_id) AS t1 " +
                  "WHERE nick = ? COLLATE NOCASE";
            statName = "winnings";
            line += "Blackjack Winnings (min. 1 round): ";
        } else if (stat.equalsIgnoreCase("winrate")) {
            sql = "SELECT nick, rounds, winnings*1.0/rounds AS winrate, " +
                      "(SELECT COUNT(*) FROM BJPlayerStat WHERE rounds > 50 AND winnings*1.0/rounds > t1.winnings*1.0/t1.rounds)+1 AS rank " +
                  "FROM (Player INNER JOIN BJPlayerStat ON Player.id = BJPlayerStat.player_id) AS t1 " +
                  "WHERE nick = ? COLLATE NOCASE";
            statName = "winrate";
            line += "Blackjack Win Rate (min. 50 rounds): ";
        } else {
            throw new IllegalArgumentException();
        }
        
        try (Connection conn = DriverManager.getConnection(dbURL)) {
            // Retrieve data from DB if possible
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, nick);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.isBeforeFirst()) {
                        line += "#" + rs.getInt("rank") + " " + Colors.WHITE + ",04 " + formatNoPing(rs.getString("nick"));
                        if (statName.equals("winrate")){
                            if (rs.getInt("rounds") < 50) {
                                line = String.format("%s (%d) has not played enough rounds of %s. A minimum of 50 rounds must be played to qualify for a win rate ranking.", formatNoPing(rs.getString("nick")), rs.getInt("rounds"), getGameNameStr());
                            } else {
                                line += " $" + formatDecimal(rs.getDouble(statName));
                            }
                        } else if (statName.equals("rounds")) {
                            if (rs.getInt("rounds") == 0) {
                                line = String.format(getMsg("player_no_rounds"), formatNoPing(rs.getString("nick")), getGameNameStr());
                            } else {
                                line += " " + formatNumber(rs.getInt(statName));
                            }
                        } else if (statName.equals("winnings")) {
                            if (rs.getInt("rounds") == 0) {
                                line = String.format(getMsg("player_no_rounds"), formatNoPing(rs.getString("nick")), getGameNameStr());
                            } else {
                                line += " $" + formatNumber(rs.getInt(statName));
                            }
                        } else if (statName.equals("bankrupts")) {
                            line += " " + formatNumber(rs.getInt(statName));
                        } else {
                            line += " $" + formatNumber(rs.getInt(statName));
                        }
                        
                        // Show rank
                        showMsg(line);
                    } else {
                        showMsg(getMsg("no_data"), formatNoPing(nick));
                    }
                }
            }
            logDBWarning(conn.getWarnings());
        } catch (SQLException ex) {
            manager.log("SQL Error: " + ex.getMessage());
        }
    }
    
    @Override
    public void showTopPlayers(String stat, int n) throws IllegalArgumentException {
        if (n < 1){
            throw new IllegalArgumentException();
        }
        
        String title = Colors.BOLD + Colors.BLACK + ",08 Top %,d-%,d";
        String list = Colors.BLACK + ",08";
        String statName = "";
        String sql = "";
        String sqlBounds = "";
        
        if (stat.equalsIgnoreCase("cash")) {
            sqlBounds = "SELECT MIN(?, 10) AS top_limit, MAX(0, MIN((SELECT COUNT(*) FROM Purse), ?)-10) AS top_offset";
            sql = "SELECT nick, cash " +
                  "FROM Player INNER JOIN Purse ON id = player_id " +
                  "ORDER BY cash DESC " +
                  "LIMIT ? OFFSET ?";
            statName = "cash";
            title += " Cash ";
        } else if (stat.equalsIgnoreCase("bank")) {
            sqlBounds = "SELECT MIN(?, 10) AS top_limit, MAX(0, MIN((SELECT COUNT(*) FROM Purse), ?)-10) AS top_offset";
            sql = "SELECT nick, bank " +
                  "FROM Player INNER JOIN Purse ON id = player_id " +
                  "ORDER BY bank DESC " +
                  "LIMIT ? OFFSET ?";
            statName = "bank";
            title += " Bank ";
        } else if (stat.equalsIgnoreCase("bankrupts")) {
            sqlBounds = "SELECT MIN(?, 10) AS top_limit, MAX(0, MIN((SELECT COUNT(*) FROM Purse), ?)-10) AS top_offset";
            sql = "SELECT nick, bankrupts " +
                  "FROM Player INNER JOIN Purse ON id = player_id " +
                  "ORDER BY bankrupts DESC " +
                  "LIMIT ? OFFSET ?";
            statName = "bankrupts";
            title += " Bankrupts ";
        } else if (stat.equalsIgnoreCase("net") || stat.equalsIgnoreCase("netcash")) {
            sqlBounds = "SELECT MIN(?, 10) AS top_limit, MAX(0, MIN((SELECT COUNT(*) FROM Purse), ?)-10) AS top_offset";
            sql = "SELECT nick, cash+bank AS netcash " +
                  "FROM Player INNER JOIN Purse ON id = player_id " +
                  "ORDER BY netcash DESC " +
                  "LIMIT ? OFFSET ?";
            statName = "netcash";
            title += " Net Cash ";
        } else if (stat.equalsIgnoreCase("winnings")){
            sqlBounds = "SELECT MIN(?, 10) AS top_limit, MAX(0, MIN((SELECT COUNT(*) FROM BJPlayerStat WHERE rounds > 0), ?)-10) AS top_offset";
            sql = "SELECT nick, winnings " +
                  "FROM Player INNER JOIN BJPlayerStat ON id = player_id " +
                  "WHERE rounds > 0 " +
                  "ORDER BY winnings DESC " +
                  "LIMIT ? OFFSET ?";
            statName = "winnings";
            title += " Blackjack Winnings (min. 1 round)";
        } else if (stat.equalsIgnoreCase("rounds")) {
            sqlBounds = "SELECT MIN(?, 10) AS top_limit, MAX(0, MIN((SELECT COUNT(*) FROM BJPlayerStat WHERE rounds > 0), ?)-10) AS top_offset";
            sql = "SELECT nick, rounds " +
                  "FROM Player INNER JOIN BJPlayerStat ON id = player_id " +
                  "WHERE rounds > 0 " +
                  "ORDER BY rounds DESC " +
                  "LIMIT ? OFFSET ?";
            statName = "rounds";
            title += " Blackjack Rounds (min. 1 round)";
        } else if (stat.equalsIgnoreCase("winrate")) {
            sqlBounds = "SELECT MIN(?, 10) AS top_limit, MAX(0, MIN((SELECT COUNT(*) FROM BJPlayerStat WHERE rounds > 50), ?)-10) AS top_offset";
            sql = "SELECT nick, winnings*1.0/rounds AS winrate " +
                  "FROM Player INNER JOIN BJPlayerStat ON id = player_id " +
                  "WHERE rounds > 50 " +
                  "ORDER BY winrate DESC " +
                  "LIMIT ? OFFSET ?";
            statName = "winrate";
            title += " Blackjack Win Rate (min. 50 rounds) ";
        } else {
            throw new IllegalArgumentException();
        }
        
        try (Connection conn = DriverManager.getConnection(dbURL)) {
            conn.setAutoCommit(false);
            int limit = 10;
            int offset = 0;
            
            // Retrieve offset
            try (PreparedStatement ps = conn.prepareStatement(sqlBounds)) {
                ps.setInt(1, n);
                ps.setInt(2, n);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.isBeforeFirst()) {
                        limit = rs.getInt("top_limit");
                        offset = rs.getInt("top_offset");
                    }
                }
            }
            
            title = String.format(title, offset+1, offset+limit);
            
            // Retrieve data from DB if possible
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                ps.setInt(2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.isBeforeFirst()) {
                        int ctr = offset + 1;
                        // Add the players in the required range
                        while (rs.next()) {
                            list += " #" + ctr++ + ": " + Colors.WHITE + ",04 ";
                            if (statName.equals("winrate")) {
                                list += formatNoPing(rs.getString("nick")) + " $" + formatDecimal(rs.getDouble(statName));
                            } else if (statName.equals("rounds") || statName.equals("bankrupts")) {
                                list += formatNoPing(rs.getString("nick")) + " " + formatNumber(rs.getInt(statName));
                            } else {
                                list += formatNoPing(rs.getString("nick")) + " $" + formatNumber(rs.getInt(statName));
                            }
                            list += " " + Colors.BLACK + ",08";
                        }
                        
                        // Output title and the list
                        showMsg(title);
                        showMsg(list);
                    } else {
                        showMsg("No %s data for %s.", statName, getGameNameStr());
                    }
                }
            }
            conn.commit();
            logDBWarning(conn.getWarnings());
        } catch (SQLException ex) {
            manager.log("SQL Error: " + ex.getMessage());
        }
    }
    
    ///////////////////////////
    //// Formatted strings ////
    ///////////////////////////
    
    @Override
    public final String getGameNameStr(){
        return formatBold(getMsg("bj_game_name"));
    }
    
    @Override
    public final String getGameRulesStr() {
        if (has("soft17hit")){
            return String.format(getMsg("bj_rules_soft17hit"), deck.getNumberDecks(), get("shufflepoint"), get("minbet"));
        } else {
            return String.format(getMsg("bj_rules_soft17stand"), deck.getNumberDecks(), get("shufflepoint"), get("minbet"));
        }
    }
    
    @Override
    public final String getGameStatsStr(){
        Record record = loadDBGameTotals();
        return String.format(getMsg("bj_stats"), record.get("total_players"), getGameNameStr(), record.get("total_rounds"), record.get("total_winnings"));
    }
    
    private static String getWinStr(){
        return Colors.GREEN+",01"+" WIN "+Colors.NORMAL;
    }
    private static String getLossStr(){
        return Colors.RED+",01"+" LOSS "+Colors.NORMAL;
    }
    private static String getSurrStr(){
        return Colors.RED+",01"+" SURR "+Colors.NORMAL;
    }
    private static String getPushStr(){
        return Colors.WHITE+",01"+" PUSH "+Colors.NORMAL;
    }
}