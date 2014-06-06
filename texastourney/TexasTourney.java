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
import irccasino.cardgame.CardGame;
import irccasino.cardgame.Hand;
import irccasino.cardgame.Player;
import irccasino.texaspoker.PokerPot;
import irccasino.texaspoker.PokerPlayer;
import irccasino.texaspoker.PokerSimulator;
import irccasino.texaspoker.TexasPoker;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.pircbotx.*;

/**
 * Extends TexasPoker to work as a tournament mode.
 * @author Yizhe Shen
 */
public class TexasTourney extends TexasPoker {
    
    ArrayList<Player> newOutList;
    TourneyStat tourneyStats;
    int tourneyRounds;
    int numOuts;
    boolean newPlayerOut;
    
    public TexasTourney() {
        super();
    }
    
    /**
     * The default constructor for TexasTourney, subclass of TexasPoker.
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
        super(parent, commChar, gameChannel, customINI);
    }
    
    /////////////////////////////////////////
    //// Methods that process IRC events ////
    /////////////////////////////////////////
    
    @Override
    public void processCommand(User user, String command, String[] params){
        String nick = user.getNick();
        String host = user.getHostmask();
        
        // Commands available in TexasTourney.
        if (command.equalsIgnoreCase("join") || command.equalsIgnoreCase("j")){
            join(nick, host);
        } else if (command.equalsIgnoreCase("leave") || command.equalsIgnoreCase("quit") || command.equalsIgnoreCase("l") || command.equalsIgnoreCase("q")){
            leave(nick, params);
        } else if (command.equalsIgnoreCase("start") || command.equalsIgnoreCase("go")) {
            start(nick, params);
        } else if (command.equalsIgnoreCase("stop") || command.equalsIgnoreCase("cancel")) {
            stop(nick, params);
        } else if (command.equalsIgnoreCase("cash")) {
            cash(nick, params);
        } else if (command.equalsIgnoreCase("tourneys") || command.equalsIgnoreCase("rounds")) {
            rounds(nick, params);
        } else if (command.equalsIgnoreCase("wins")) {
            wins(nick, params);
        } else if (command.equalsIgnoreCase("winrate")) {
            winrate(nick, params);
        } else if (command.equalsIgnoreCase("player") || command.equalsIgnoreCase("p")){
            player(nick, params);
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
        } else if (command.equalsIgnoreCase("players")) {
            players(nick, params);
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
        } else if (command.equalsIgnoreCase("grules")) {
            grules(nick, params);
        } else if (command.equalsIgnoreCase("ghelp")) {
            ghelp(nick, params);
        } else if (command.equalsIgnoreCase("gcommands")) {
            gcommands(user, nick, params);
        } else if (command.equalsIgnoreCase("game")) {
            game(nick, params);
        /* Op commands */
        } else if (command.equalsIgnoreCase("fj") || command.equalsIgnoreCase("fjoin")){
            fjoin(user, nick, params);
        } else if (command.equalsIgnoreCase("fl") || command.equalsIgnoreCase("fq") || command.equalsIgnoreCase("fquit") || command.equalsIgnoreCase("fleave")){
            fleave(user, nick, params);
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
        } else if (command.equalsIgnoreCase("shuffle")){
            shuffle(user, nick, params);
        } else if (command.equalsIgnoreCase("reload")) {
            reload(user, nick, params);
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
        }
    }
    
    @Override
    protected void processPart(User user) {
        processQuit(user);
    }
    
    @Override
    protected void processQuit(User user){
        String nick = user.getNick();
        if (isJoined(nick)){
            leave(nick);
        }
    }
    
    @Override
    protected void processNickChange(User user, String oldNick, String newNick){
        String host = user.getHostmask();
        if (!isJoined(oldNick)) {
            // Do nothing
        } else if (!isInProgress()) {
            informPlayer(newNick, getMsg("nick_change"));
            leave(oldNick);
            join(newNick, host);
        } else {
            informPlayer(newNick, getMsg("tt_nick_change"));
            leave(oldNick);
        }
    }
    
    @Override
    protected void processKick(User recip) {
        processQuit(recip);
    }

    /////////////////////////
    //// Command methods ////
    /////////////////////////
    
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
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("tt_started_unable_join"));
        } else {
            addPlayer(nick, host);
        }
    }
    
    @Override
    protected void leave(String nick, String[] params) {
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else {
            leave(nick);
        }
    }
    
    /**
     * Starts a new tournament.
     * @param nick
     * @param params 
     */
    @Override
    protected void start(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("tt_started"));
        } else if (joined.size() < get("minplayers")) {
            showMsg(getMsg("no_players"));
        } else {
            state = PokerState.PRE_START;
            startTime = System.currentTimeMillis() / 1000;
            showMsg(formatHeader(" " + getMsg("tt_new_tourney") + " "));
            showStartRound();
            setStartRoundTask();
        }
    }
    
    /**
     * Requests that the tournament be canceled.
     * @param nick
     * @param params 
     */
    @Override
    protected void stop(String nick, String[] params) {
        if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
        } else if (isBlacklisted(nick)) {
            informPlayer(nick, getMsg("tt_no_cancel"));
        } else if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else {
            requestCancel(nick);
        }
    }
    
    @Override
    protected void cash(String nick, String[] params) {
        if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
        } else if (params.length > 0){
            showPlayerCash(params[0]);
        } else {
            showPlayerCash(nick);
        }
    }
    
    /**
     * Displays the number of tournaments played for a player.
     * @param nick
     * @param params 
     */
    @Override
    protected void rounds(String nick, String[] params) {
        if (params.length > 0){
            showPlayerTourneysPlayed(params[0]);
        } else {
            showPlayerTourneysPlayed(nick);
        }
    }
    
    /**
     * Displays the number of tournament wins for a player.
     * @param nick
     * @param params 
     */
    protected void wins(String nick, String[] params) {
        if (params.length > 0){
            showPlayerTourneyWins(params[0]);
        } else {
            showPlayerTourneyWins(nick);
        }
    }
    
    @Override
    protected void bet(String nick, String[] params) {
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
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
    
    @Override
    protected void call(String nick, String[] params) {
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
        } else if (findJoined(nick) != currentPlayer){
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            call();
        }
    }
    
    @Override
    protected void check(String nick, String[] params) {
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
        } else if (findJoined(nick) != currentPlayer){
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            check();
        }
    }
    
    @Override
    protected void fold(String nick, String[] params) {
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
        } else if (findJoined(nick) != currentPlayer){
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            fold();
        }
    }

    @Override
    protected void raise(String nick, String[] params) {
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
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

    @Override
    protected void allin(String nick, String[] params) {
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
        } else if (findJoined(nick) != currentPlayer){
            informPlayer(nick, getMsg("wrong_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            bet(currentPlayer.getInteger("cash"));
        }
    }
    
    @Override
    protected void community(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else {
            showCommunityCards(false);
        }
    }
    
    @Override
    protected void hand(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
        } else {
            PokerPlayer p = (PokerPlayer) findJoined(nick);
            informPlayer(nick, getMsg("tp_hand"), p.getHand());
        }
    }
    
    @Override
    protected void turn(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else {
            showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), 
                    currentBet - currentPlayer.getInteger("bet"), 
                    currentPlayer.getInteger("bet"), currentBet, getCashInPlay(), 
                    currentPlayer.getInteger("cash") - currentPlayer.getInteger("bet"));
        }
    }
    
    /**
     * Displays the players out of the tournament.
     * @param nick
     * @param params 
     */
    @Override
    protected void blacklist(String nick, String[] params) {
        if (!isInProgress()) {
            showMsg(getMsg("tt_no_start"));
        } else {
            showMsg(getMsg("tt_out_of_tourney"), getPlayerListString(blacklist));
        }
    }
    
    @Override
    protected void rank(String nick, String[] params) {
        if (isInProgress()) {
            informPlayer(nick, getMsg("tt_wait_for_end"));
        } else if (params.length > 1){
            try {
                showPlayerRank(params[1], params[0]);
            } catch (IllegalArgumentException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        } else if (params.length == 1){
            try {
                showPlayerRank(nick, params[0]);
            } catch (IllegalArgumentException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        } else {
            showPlayerRank(nick, "wins");
        }
    }
    
    @Override
    protected void top(String nick, String[] params) {
        if (isInProgress()) {
            informPlayer(nick, getMsg("tt_wait_for_end"));
        } else if (params.length > 1){
            try {
                showTopPlayers(params[1], Integer.parseInt(params[0]));
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
    
    @Override
    protected void stats(String nick, String[] params) {
        if (isInProgress()) {
            informPlayer(nick, getMsg("tt_wait_for_end"));
        } else {
            showMsg(getGameStatsStr());
        }
    }
    
    @Override
    public void fjoin(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else {
            String fNick = params[0];
            Iterator<User> it = channel.getUsers().iterator();
            while(it.hasNext()){
                User u = it.next();
                if (u.getNick().equalsIgnoreCase(fNick)){
                    CardGame game = manager.getGame(fNick);
                    if (joined.size() == get("maxplayers")){
                        informPlayer(nick, getMsg("max_players"));
                    } else if (isJoined(fNick)) {
                        informPlayer(nick, getMsg("is_joined_nick"), fNick);
                    } else if (isBlacklisted(fNick) || manager.isBlacklisted(fNick)) {
                        informPlayer(nick, getMsg("on_blacklist_nick"), fNick);
                    } else if (game != null) {
                        informPlayer(nick, getMsg("is_joined_other_nick"), fNick, game.getGameNameStr(), game.getChannel().getName());
                    } else if (isInProgress()) {
                        informPlayer(nick, getMsg("tt_started_unable_join"));
                    } else {
                        addPlayer(u.getNick(), u.getHostmask());
                    }
                    return;
                }
            }
            informPlayer(nick, getMsg("nick_not_found"), fNick);
        }
    }
    
    @Override
    protected void fleave(User user, String nick, String[] params) {
        String fNick = params[0];
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else if (!isJoined(fNick)){
            informPlayer(nick, getMsg("no_join_nick"), fNick);
        } else if (!isInProgress()) {
            Player p = findJoined(fNick);
            removeJoined(fNick);
            showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
        } else {
            leave(fNick);
        }
    }
    
    @Override
    protected void fstart(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("tt_started"));
        } else if (joined.size() < get("minplayers")) {
            showMsg(getMsg("no_players"));
        } else {
            state = PokerState.PRE_START;
            showMsg(formatHeader(getMsg("tt_new_tourney")));
            showStartRound();
            setStartRoundTask();
        }
    }
    
    @Override
    protected void fstop(User user, String nick, String[] params) {
        // Equivalent to canceling the tournament
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
        } else {
            cancelStartRoundTask();
            cancelIdleOutTask();
            for (Player p : joined) {
                resetPlayer(p);
            }
            resetGame();
            showMsg(getMsg("tt_end_round"), ++tourneyRounds);
            showMsg(getMsg("tt_no_winner_cancel"));
            resetTourney();
        }
    }
    
    @Override
    protected void fbet(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
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
    
    @Override
    protected void fallin(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            bet(currentPlayer.getInteger("cash"));
        }
    }
    
    @Override
    protected void fraise(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
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
    
    @Override
    protected void fcall(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            call();
        }
    }
    
    @Override
    protected void fcheck(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            check();
        }
    }
    
    @Override
    protected void ffold(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("tt_no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("nobody_turn"));
        } else if (state.equals(PokerState.CONTINUE_ROUND)) {
            informPlayer(nick, getMsg("game_lagging"));
        } else {
            fold();
        }
    }
    
    @Override
    protected void shuffle(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("tt_wait_for_end"));
        } else {
            shuffleDeck();
        }
    }
    
    @Override
    protected void reload(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("tt_wait_for_end"));
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
    
    @Override
    protected void settings(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("tt_wait_for_end"));
        } else {
            informPlayer(nick, getGameNameStr() + " settings:");
            informPlayer(nick, getSettingsStr());
        }
    }
    
    @Override
    protected void set(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("tt_wait_for_end"));
        } else if (params.length < 2){
            informPlayer(nick, getMsg("no_parameter"));
        } else {
            try {
                String setting = params[0].toLowerCase();
                int value = Integer.parseInt(params[1]);
                set(setting, value);
                saveIniFile();
                showMsg(getMsg("setting_updated"), setting);
            } catch (IllegalArgumentException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    @Override
    protected void get(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("tt_wait_for_end"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else {
            try {
                String setting = params[0].toLowerCase();
                int value = get(params[0].toLowerCase());
                showMsg(getMsg("setting"), setting, value);
            } catch (IllegalArgumentException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /////////////////////////////////
    //// Game management methods ////
    /////////////////////////////////
    
    @Override
    public void addPlayer(String nick, String host) {
        addPlayer(new TourneyPokerPlayer(nick));
    }
    
    @Override
    public void leave(String nick) {
        PokerPlayer p = (PokerPlayer) findJoined(nick);
        
        switch (state) {
            case NONE:
                removeJoined(p);
                showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
                break;
            case PRE_START:
                removeJoined(p);
                showMsg(getMsg("tt_unjoin"), p.getNickStr());
                blacklist.add(0, p);
                break;
            case BETTING:
                p.put("quit", true);
                informPlayer(p.getNick(), getMsg("remove_end_round"));
                if (p == currentPlayer){
                    fold();
                } else if (!p.has("fold")){
                    p.put("fold", true);
                    // Remove this player from any existing pots
                    if (currentPot != null && currentPot.isEligible(p)){
                        currentPot.disqualify(p);
                    }
                    for (int ctr = 0; ctr < pots.size(); ctr++){
                        PokerPot cPot = pots.get(ctr);
                        if (cPot.isEligible(p)){
                            cPot.disqualify(p);
                        }
                    }
                    // If there is only one player who hasn't folded,
                    // force call on that remaining player (whose turn it must be)
                    if (getNumberNotFolded() == 1){
                        call();
                    }
                }
                break;
            case BLINDS: case CONTINUE_ROUND:
                p.put("quit", true);
                p.put("fold", true);
                informPlayer(p.getNick(), getMsg("remove_end_round"));
                break;
            case SHOWDOWN: case END_ROUND:
                p.put("quit", true);
                informPlayer(p.getNick(), getMsg("remove_end_round"));
                break;
            default:
                break;
        }
    }
    
    @Override
    protected void removeJoined(Player p){
        User user = findUser(p.getNick());
        joined.remove(p);
        if (user != null){
            manager.deVoice(channel, user);
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
         * Find the next player. Look for a player who can bet that is not the 
         * currentPlayer or the topBettor. If we reach the currentPlayer or 
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
                // Show final community if required
                if (settings.get("revealcommunity") == 1 && betState.equals(PokerBet.RIVER)){
                    showCommunityCards(true);
                }
            }
            
            endRound();
        } else if (nextPlayer == topBettor || nextPlayer == currentPlayer) {
            // If we reach the firstPlayer or topBettor, then we have reached 
            // the end of a round of betting and we should deal community cards.
            // Reset minimum raise (override)
            minRaise = (int) (get("minbet")*(Math.pow(2, tourneyRounds/get("doubleblinds") + numOuts)));
            addBetsToPot();
            currentPlayer = null;
            topBettor = null;
            
            // If all community cards have been dealt, move to end of round.
            // Otherwise, deal more community cards
            if (betState.equals(PokerBet.RIVER)){
                endRound();
            } else if (getNumberCanBet() < 2 && getNumberNotFolded() > 1) {
                /* 
                 * Check for showdown. Show player hands and their win/tie 
                 * probabilities immediately and each time additional community
                 * cards are revealed. Adds a dramatic delay between each reveal.
                 */
                state = PokerState.SHOWDOWN;
                PokerSimulator sim;
                ArrayList<PokerPlayer> players = pots.get(0).getEligibles();
                
                while (!betState.equals(PokerBet.RIVER)) {
                    sim = new PokerSimulator(players, community);
                    String showdownStr = formatHeader(" Showdown: ") + " ";
                    for (PokerPlayer p : players) {
                        showdownStr += p.getNickStr() + " (" + p.getHand() + "||" + formatBold(Math.round(sim.getWinPct(p)) + "/" + Math.round(sim.getTiePct(p)) + "%%") + "), ";
                    }
                    showMsg(showdownStr);

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
            showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), 
                    currentBet - currentPlayer.getInteger("bet"), 
                    currentPlayer.getInteger("bet"), currentBet, getCashInPlay(), 
                    currentPlayer.getInteger("cash") - currentPlayer.getInteger("bet"));
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
                    showMsg(getMsg("tt_unjoin"), p.getNickStr());
                    ctr--;
                    newPlayerOut = true;
                    newOutList.add(p);
                // Quitters
                } else if (p.has("quit")) {
                    blacklist.add(0, p);
                    removeJoined(p);
                    showMsg(getMsg("tt_unjoin"), p.getNickStr());
                    ctr--;
                }
                
                resetPlayer(p);
            }
        }
        resetGame();
        showMsg(getMsg("tt_end_round"), ++tourneyRounds);
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
        tourneyStats = null;
        devoiceAll();
        showMsg(getMsg("game_end"), getGameNameStr());
        joined.clear();
        blacklist.clear();
        newOutList.clear();
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
    
    public void resetTourney() {
        state = PokerState.NONE;
        betState = PokerBet.NONE;
        tourneyRounds = 0;
        numOuts = 0;
        newPlayerOut = false;
        newOutList.clear();
        devoiceAll();
        joined.clear();
        blacklist.clear();
    }
    
    /**
     * Checks to see if a tournament can continue.
     */
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
            saveDBPlayerData(p);
            showMsg(getMsg("tt_winner"), p.getNickStr(), p.get("ttwins"));
            for (int ctr = 0; ctr < blacklist.size(); ctr++) {
                p = (PokerPlayer) blacklist.get(ctr);
                p.add("ttplayed", 1);
                savePlayerData(p);
                saveDBPlayerData(p);
            }
            
            // Display tournament results
            showTourneyResults();
            
            tourneyStats.add("numtourneys", 1);
            if (tourneyStats.getBiggestTourney() < joined.size() + blacklist.size()) {
                tourneyStats.setWinner(new PokerPlayer(joined.get(0).getNick()));
                tourneyStats.getPlayers().clear();
                tourneyStats.addPlayer(new PokerPlayer(joined.get(0).getNick()));
                for (Player pp : blacklist) {
                    tourneyStats.addPlayer(new PokerPlayer(pp.getNick()));
                }
            }
            
            // Save game stats
            endTime = System.currentTimeMillis() / 1000;
            saveGameStats();
            saveDBGameStats();
            
            // Reset tournament
            resetTourney();
        // Automatically start a new round if more than 1 player left
        } else {
            if (tourneyRounds % get("doubleblinds") == 0) {
                int newBlind = (int) (get("minbet")*(Math.pow(2, tourneyRounds/get("doubleblinds") + numOuts)));
                showMsg(getMsg("tt_double_blinds"), tourneyRounds, newBlind/2, newBlind);
            }
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
            state = PokerState.PRE_START;
            betState = PokerBet.NONE;
            showStartRound();
            setStartRoundTask();
        }
    }
    
    @Override
    protected void resetPlayer(Player p) {
        discardPlayerHand((TourneyPokerPlayer) p);
        p.clear("fold");
        p.clear("quit");
        p.clear("allin");
        p.clear("change");
        p.clear("cancel");
    }
    
    /**
     * Sets the bets for the small and big blinds.
     */
    @Override
    protected void setBlindBets(){
        // Calculate the current blind bet
        int newBlind = (int) (get("minbet")*(Math.pow(2, tourneyRounds/get("doubleblinds") + numOuts)));
        
        // Set the small blind
        if (newBlind/2 > smallBlind.getInteger("cash")) {
            smallBlind.put("allin", true);
            smallBlind.put("bet", smallBlind.get("cash"));
        } else {
            smallBlind.put("bet", newBlind/2);
        }
        
        // Set the big blind
        if (newBlind > bigBlind.getInteger("cash")) {
            bigBlind.put("allin", true);
            bigBlind.put("bet", bigBlind.get("cash"));
        } else {
            bigBlind.put("bet", newBlind);
        }
        
        // Set the current bet to the bigger of the two blinds.
        currentBet = newBlind;
        minRaise = newBlind;
    }
    
    /**
     * Requests a cancel for the specified player. Cancels the tournament if
     * all players request cancellation.
     * @param nick 
     */
    protected void requestCancel(String nick) {
        TourneyPokerPlayer p = (TourneyPokerPlayer) findJoined(nick);
        if (p.has("cancel")) {
            informPlayer(nick, getMsg("tt_already_cancel"));
        } else {
            showMsg(getMsg("tt_cancel"), p.getNickStr(false));
            p.put("cancel", true);
            
            // Check if all players have made a request
            for (Player pp : joined) {
                if (!pp.has("cancel")) {
                    return;
                }
            }
            
            // If we get down here, then cancel the tournament
            cancelStartRoundTask();
            cancelIdleOutTask();
            showMsg(getMsg("tt_cancel_tourney"));
            for (Player pp : joined) {
                resetPlayer(pp);
            }
            resetGame();
            showMsg(getMsg("tt_end_round"), ++tourneyRounds);
            showMsg(getMsg("tt_no_winner_cancel"));
            resetTourney();
        }
    }
    
    ////////////////////////////////////////
    //// Game initialization management ////
    ////////////////////////////////////////
    
    @Override
    protected void initSettings() {
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
        settings.put("revealcommunity", 0);
        settings.put("doubleblinds", 10);
        settings.put("doubleonbankrupt", 0);
        settings.put("ping", 600);
    }
    
    @Override
    protected void initCustom(){
        name = "texastourney";
        helpFile = "texastourney.help";
        newOutList = new ArrayList<>();
        tourneyStats = new TourneyStat();
        deck = new CardDeck();
        deck.shuffleCards();
        pots = new ArrayList<>();
        community = new Hand();
        
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
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(iniFile)))) {
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
            out.println("minplayers=" + get("minplayers"));
            out.println("#The wait time in seconds after the start command is given");
            out.println("startwait=" + get("startwait"));
            out.println("#The wait time in seconds in between reveals during a showdown");
            out.println("showdown=" + get("showdown"));
            out.println("#Whether or not to reveal community when not required");
            out.println("revealcommunity=" + get("revealcommunity"));
            out.println("#The number of rounds in between doubling of blinds");
            out.println("doubleblinds=" + get("doubleblinds"));
            out.println("#Whether or not to double blinds when a player goes out");
            out.println("doubleonbankrupt=" + get("doubleonbankrupt"));
            out.println("#The rate-limit of the ping command");
            out.println("ping=" + get("ping"));
        } catch (IOException e) {
            manager.log("Error creating " + iniFile + "!");
        }
    }
    
    /////////////////////////////////////////
    //// Player stats management methods ////
    /////////////////////////////////////////
    
    /**
     * For tournament mode, we don't want to load a player's cash accumulated
     * from other games.
     * @param p the player to load
     */
    @Override
    protected void loadPlayerData(Player p) {
        ArrayList<Player> records = loadPlayerFile();
        if (records != null) {
            p.put("cash", get("cash"));
            for (Player statLine : records) {
                if (p.getNick().equalsIgnoreCase(statLine.getNick())) {
                    p.put("ttwins", statLine.get("ttwins"));
                    p.put("ttplayed", statLine.get("ttplayed"));
                    break;
                }
            }
        }
    }
    
    /**
     * For tournament mode, we don't want to save a player's cash. Also, only
     * values which are applicable will be overwritten.
     * @param p the player to save
     */
    @Override
    protected void savePlayerData(Player p){
        boolean found = false;
        ArrayList<Player> records = loadPlayerFile();
        
        if (records != null) {
            for (Player record : records) {
                if (p.getNick().equalsIgnoreCase(record.getNick())) {
                    record.put("ttwins", p.get("ttwins"));
                    record.put("ttplayed", p.get("ttplayed"));
                    found = true;
                    break;
                }
            }
            if (!found) {
                Player record = new Player("");
                record.put("nick", p.getString("nick"));
                record.put("cash", 0);
                record.put("bank", 0);
                record.put("bankrupts", 0);
                record.put("bjwinnings", 0);
                record.put("bjrounds", 0);
                record.put("tpwinnings", 0);
                record.put("tprounds", 0);
                record.put("ttwins", p.get("ttwins"));
                record.put("ttplayed", p.get("ttplayed"));
                records.add(record);
            }

            savePlayerFile(records);
        }
    }
    
    @Override
    public int getTotalPlayers(){
        int total = 0;
        try (Connection conn = DriverManager.getConnection(dbURL)) {
            // Retrieve record count for TTPlayerStat table where tourneys > 0
            String sql = "SELECT count(*) FROM TTPlayerStat WHERE tourneys > 0";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.isBeforeFirst()) {
                        total = rs.getInt(1);
                    }
                }
            }
            logDBWarning(conn.getWarnings());
        } catch (SQLException ex) {
            manager.log("SQL Error: " + ex.getMessage());
        }
        return total;
    }  
    
    @Override
    protected void loadDBPlayerData(Player p) {
        try (Connection conn = DriverManager.getConnection(dbURL)) {
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
            
            // Add new player if not found in Player table
            if (!p.has("id")) {
                sql = "INSERT INTO Player (nick, time_created) VALUES(?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, p.getNick());
                    ps.setLong(2, System.currentTimeMillis() / 1000);
                    ps.executeUpdate();
                    p.put("id", ps.getGeneratedKeys().getInt(1));
                }
            }
            
            // Retrieve data from TTPlayerStat table if possible
            boolean found = false;
            sql = "SELECT player_id, tourneys, points " +
                  "FROM TTPlayerStat WHERE player_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, p.getInteger("id"));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.isBeforeFirst()) {
                        found = true;
                        p.put("ttplayed", rs.getInt("tourneys"));
                        p.put("ttwins", rs.getInt("points"));
                    }
                }
            }
            
            // Add new record if not found in TPPlayerStat table
            if (!found) {
                sql = "INSERT INTO TTPlayerStat (player_id, tourneys, points) " +
                      "VALUES(?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, p.getInteger("id"));
                    ps.setInt(2, p.getInteger("ttplayed"));
                    ps.setInt(3, p.getInteger("ttwins"));
                    ps.executeUpdate();
                }
            }
            
            logDBWarning(conn.getWarnings());
        } catch (SQLException ex) {
            manager.log("SQL Error: " + ex.getMessage());
        }
    }
    
    @Override
    protected void saveDBPlayerData(Player p) {
        try (Connection conn = DriverManager.getConnection(dbURL)) {
            // Update data in TTPlayerStat table
            String sql = "UPDATE TTPlayerStat SET tourneys = ?, points = ? " +
                         "WHERE player_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, p.getInteger("ttplayed"));
                ps.setInt(2, p.getInteger("ttwins"));
                ps.setInt(3, p.getInteger("id"));
                ps.executeUpdate();
            }
            
            logDBWarning(conn.getWarnings());
        } catch (SQLException ex) {
            manager.log("SQL Error: " + ex.getMessage());
        }
    }
    
    ///////////////////////////////////////
    //// Game stats management methods ////
    ///////////////////////////////////////
    
    @Override
    public void loadGameStats() {
        try (BufferedReader in = new BufferedReader(new FileReader("housestats.txt"))) {
            String str;
            StringTokenizer st;
            while (in.ready()) {
                str = in.readLine();
                if (str.startsWith("#texastourney")) {
                    while (in.ready()) {
                        str = in.readLine();
                        if (str.startsWith("#")) {
                            break;
                        }
                        st = new StringTokenizer(str);
                        tourneyStats.put("numtourneys", Integer.parseInt(st.nextToken()));
                        tourneyStats.setWinner(new PokerPlayer(st.nextToken()));
                        while (st.hasMoreTokens()) {
                            tourneyStats.addPlayer(new PokerPlayer(st.nextToken()));
                        }
                    }
                    break;
                }
            }
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
        ArrayList<String> lines = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new FileReader("housestats.txt"))) {
            String str;
            while (in.ready()) {
                //Add all lines until we find texaspoker lines
                str = in.readLine();
                lines.add(str);
                if (str.startsWith("#texastourney")) {
                    found = true;
                    /* Store the index where texastourney stats go so they can be
                    * overwritten. */
                    index = lines.size();
                    //Skip existing texastourney lines but add all the rest
                    while (in.ready()) {
                        str = in.readLine();
                        if (str.startsWith("#")) {
                            lines.add(str);
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            /* housestats.txt is not found */
            manager.log("Error reading housestats.txt!");
        }
        if (!found) {
            lines.add("#texastourney");
            index = lines.size();
        }
        lines.add(index, tourneyStats.get("numtourneys") + " " + tourneyStats.getWinner().getNick() + " " + tourneyStats.getPlayersString());
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("housestats.txt")))) {
            for (int ctr = 0; ctr < lines.size(); ctr++) {
                out.println(lines.get(ctr));
            }
        } catch (IOException e) {
            manager.log("Error writing to housestats.txt!");
        }
    }  
    
    @Override
    protected void loadDBGameStats() {
        
    }
    
    @Override
    protected void saveDBGameStats() {
        int tourneyID;
        try (Connection conn = DriverManager.getConnection(dbURL)) {
            // Insert data into TTTourney table
            String sql = "INSERT INTO TTTourney (start_time, end_time, rounds) " +
                         "VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, startTime);
                ps.setLong(2, endTime);
                ps.setInt(3, tourneyRounds);
                ps.executeUpdate();
                tourneyID = ps.getGeneratedKeys().getInt(1);
            }
            
            // Insert winner into TTPlayerTourney table
            sql = "INSERT INTO TTPlayerTourney (player_id, tourney_id, " +
                  "result) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, joined.get(0).getInteger("id"));
                ps.setInt(2, tourneyID);
                ps.setBoolean(3, Boolean.TRUE);
                ps.executeUpdate();
            }
            
            for (Player p : blacklist) {
                // Insert other players into TTPlayerTourney table
                sql = "INSERT INTO TTPlayerTourney (player_id, tourney_id, " +
                      "result) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, p.getInteger("id"));
                    ps.setInt(2, tourneyID);
                    ps.setBoolean(3, Boolean.FALSE);
                    ps.executeUpdate();
                }
            }
            
            logDBWarning(conn.getWarnings());
        } catch (SQLException ex) {
            manager.log("SQL Error: " + ex.getMessage());
        }
    }
    
    /////////////////////////////////////////////////////////////
    //// Message output methods for Texas Hold'em Tournament ////
    /////////////////////////////////////////////////////////////
    
    @Override
    public void showResults(){
        ArrayList<PokerPlayer> players;
        
        // Show introduction to end results and sort players by hand
        showMsg(formatHeader(" Results: "));
        players = pots.get(0).getEligibles();
        Collections.sort(players);
        Collections.reverse(players);
        
        // Show each remaining player's hand if more than one player unfolded
        if (players.size() > 1){
            for (PokerPlayer p : players) {
                showMsg(getMsg("tp_player_result"), p.getNickStr(false), p.getHand(), p.getPokerHand().getName(), p.getPokerHand());
            }
        }
        
        // Find the winner(s) from each pot
        for (int ctr = 0; ctr < pots.size(); ctr++){
            currentPot = pots.get(ctr);
            players = currentPot.getEligibles();
            Collections.sort(players);
            Collections.reverse(players);
            
            int winners = 1;
            int potTotal = currentPot.getTotal();
            
            // Determine number of winners
            for (int ctr2 = 1; ctr2 < players.size(); ctr2++){
                if (players.get(0).compareTo(players.get(ctr2)) == 0){
                    winners++;
                }
            }
            
            // Output winners
            for (int ctr2 = 0; ctr2 < winners; ctr2++){
                PokerPlayer p = players.get(ctr2);
                p.add("cash", potTotal/winners);
                p.add("tpwinnings", potTotal/winners);
                p.add("change", potTotal/winners);
                showMsg(Colors.YELLOW+",01 Pot #" + (ctr+1) + ": " + Colors.NORMAL + " " + 
                    p.getNickStr() + " wins $" + formatNumber(potTotal/winners) + 
                    ". (" + getPlayerListString(players) + ")");
            }
        }
    }
    
    /**
     * Displays the final results of a tournament.
     */
    public void showTourneyResults() {
        String msg = "";
        
        // Append title and the players in the order in which they placed
        msg += formatHeader(" Final Standings: ") + " ";
        msg += " " + formatBold("#1:") + " " + joined.get(0).getNickStr() + " ";
        for (int ctr = 0; ctr < blacklist.size(); ctr++) {
            msg += " " + formatBold("#" + (ctr + 2) + ":") + " " + blacklist.get(ctr).getNickStr() + " ";
        }
        showMsg(msg);
    }    
    
    @Override
    protected void showStartRound(){
        showMsg(getMsg("tt_start_round"), tourneyRounds + 1, get("startwait"));
    }
    
    /**
     * Displays the number of tournaments the specified player as played.
     * @param nick 
     */
    public void showPlayerTourneysPlayed(String nick){
        if (isBlacklisted(nick)) {
            Player p = findBlacklisted(nick);
            showMsg(getMsg("tt_player_played"), p.getNick(false), p.get("ttplayed"));
        } else if (isJoined(nick)) {
            Player p = findJoined(nick);
            showMsg(getMsg("tt_player_played"), p.getNick(false), p.get("ttplayed"));
        } else {
            Player record = loadPlayerRecord(nick);
            if (record == null) {
                showMsg(getMsg("no_data"), formatNoPing(nick));
            } else {
                showMsg(getMsg("tt_player_played"), record.getNick(false), record.get("ttplayed"));
            }
        }
    }
    
    /**
     * Displays the number of tournament wins for the specified player.
     * @param nick 
     */
    public void showPlayerTourneyWins(String nick){
        if (isBlacklisted(nick)) {
            Player p = findBlacklisted(nick);
            showMsg(getMsg("tt_player_wins"), p.getNick(false), p.get("ttwins"));
        } else if (isJoined(nick)) {
            Player p = findJoined(nick);
            showMsg(getMsg("tt_player_wins"), p.getNick(false), p.get("ttwins"));
        } else {
            Player record = loadPlayerRecord(nick);
            if (record == null) {
                showMsg(getMsg("no_data"), formatNoPing(nick));
            } else {
                showMsg(getMsg("tt_player_wins"), record.getNick(false), record.get("ttwins"));
            }
        }
    }
    
    @Override
    public void showPlayerWinRate(String nick) {
        if (isBlacklisted(nick)) {
            Player p = findBlacklisted(nick);
            if (p.getInteger("ttplayed") == 0) {
                showMsg(getMsg("tt_player_no_tourneys"), p.getNick(false));
            } else {
                showMsg(getMsg("tt_player_winrate"), p.getNick(false), Math.round((double) p.get("ttwins")/ (double) p.get("ttplayed") * 100));
            }
        } else if (isJoined(nick)) {
            Player p = findJoined(nick);
            if (p.getInteger("ttplayed") == 0) {
                showMsg(getMsg("tt_player_no_tourneys"), p.getNick(false));
            } else {
                showMsg(getMsg("tt_player_winrate"), p.getNick(false), Math.round((double) p.get("ttwins")/ (double) p.get("ttplayed") * 100));
            }
        } else {
            Player record = loadPlayerRecord(nick);
            if (record == null) {
                showMsg(getMsg("no_data"), formatNoPing(nick));
            }  else if (record.getInteger("ttplayed") == 0) {
                showMsg(getMsg("tt_player_no_tourneys"), record.getNick(false));
            } else {
                showMsg(getMsg("tt_player_winrate"), record.getNick(false), Math.round((double) record.get("ttwins")/ (double) record.get("ttplayed") * 100));
            }
        }
    }
    
    @Override
    public void showPlayerAllStats(String nick){
        if (isBlacklisted(nick)) {
            Player p = findBlacklisted(nick);
            showMsg(getMsg("tt_player_all_stats"), p.getNick(false), p.get("ttwins"), p.get("ttplayed"));
        } else if (isJoined(nick)) {
            Player p = findJoined(nick);
            showMsg(getMsg("tt_player_all_stats"), p.getNick(false), p.get("ttwins"), p.get("ttplayed"));
        } else {
            Player record = loadPlayerRecord(nick);
            if (record == null) {
                showMsg(getMsg("no_data"), formatNoPing(nick));
            } else {
                showMsg(getMsg("tt_player_all_stats"), record.getNick(false), record.get("ttwins"), record.get("ttplayed"));
            }
        }
    }

    @Override
    public void showPlayerRank(String nick, String stat) throws IllegalArgumentException {
        if (getPlayerStat(nick, "exists") != 1){
            showMsg(getMsg("no_data"), formatNoPing(nick));
            return;
        }
        
        ArrayList<Player> records = loadPlayerFile();
        
        if (records != null) {
            Player aRecord;
            int length = records.size();
            String line = Colors.BLACK + ",08";
            
            if (stat.equalsIgnoreCase("winrate")) {
                int highIndex, rank = 0;
                ArrayList<String> nicks = new ArrayList<>();
                ArrayList<Integer> winrates = new ArrayList<>();
                
                for (int ctr = 0; ctr < length; ctr++) {
                    aRecord = records.get(ctr);
                    nicks.add(aRecord.getNick());
                    if (aRecord.getInteger("ttplayed") == 0){
                        winrates.add(0);
                    } else {
                        winrates.add((int) Math.round((double) aRecord.get("ttwins") / (double) aRecord.get("ttplayed") * 100));
                    }
                }
                
                line += "Texas Hold'em Tournament Win Rate: ";
                
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
                        line += "#" + rank + " " + Colors.WHITE + ",04 " + formatNoPing(nicks.get(highIndex)) + " " + formatNumber(winrates.get(highIndex)) + "%% ";
                        break;
                    } else {
                        nicks.remove(highIndex);
                        winrates.remove(highIndex);
                    }
                }
            } else {
                String statName = "";
                if (stat.equals("wins")){
                    statName = "ttwins";
                    line += "Texas Hold'em Tournament Wins: ";
                } else if (stat.equals("tourneys")) {
                    statName = "ttplayed";
                    line += "Texas Hold'em Tournaments Played: ";
                } else {
                    throw new IllegalArgumentException();
                }

                // Sort based on stat
                Collections.sort(records, Player.getComparator(statName));

                // Find the player in the records and output rank
                for (int ctr = 0; ctr < length; ctr++){
                    aRecord = records.get(ctr);
                    if (nick.equalsIgnoreCase(aRecord.getNick())){
                        line += "#" + (ctr+1) + " " + Colors.WHITE + ",04 " + formatNoPing(aRecord.getNick()) + " " + formatNumber(aRecord.getInteger(statName)) + " ";
                        break;
                    }
                }
            }

            // Show rank
            showMsg(line);
        }
    }
    
    @Override
    public void showTopPlayers(String stat, int n) throws IllegalArgumentException {
        if (n < 1){
            throw new IllegalArgumentException();
        }
        
        ArrayList<Player> records = loadPlayerFile();
        
        if (records != null) {
            Player aRecord;
            int end = Math.min(n, records.size());
            int start = Math.max(end - 10, 0);
            String title = Colors.BOLD + Colors.BLACK + ",08 Top " + (start+1) + "-" + end;
            String list = Colors.BLACK + ",08";
            
            if (stat.equalsIgnoreCase("winrate")) {
                int highIndex;
                ArrayList<String> nicks = new ArrayList<>();
                ArrayList<Integer> winrates = new ArrayList<>();
                
                for (int ctr = 0; ctr < records.size(); ctr++) {
                    aRecord = records.get(ctr);
                    nicks.add(aRecord.getNick());
                    if (aRecord.getInteger("ttplayed") == 0){
                        winrates.add(0);
                    } else {
                        winrates.add((int) Math.round((double) aRecord.get("ttwins") / (double) aRecord.get("ttplayed") * 100));
                    }
                }
                
                title += " Texas Hold'em Tournament Win Rate ";
                
                // Find the player with the highest value and check if it is 
                // the requested player. Repeat until found or end.
                for (int ctr = 0; ctr < records.size(); ctr++){
                    highIndex = 0;
                    for (int ctr2 = 0; ctr2 < nicks.size(); ctr2++) {
                        if (winrates.get(ctr2) > winrates.get(highIndex)) {
                            highIndex = ctr2;
                        }
                    }
                    
                    // Only add those in the required range.
                    if (ctr >= start) {
                        list += " #" + (ctr+1) + ": " + Colors.WHITE + ",04 " + formatNoPing(nicks.get(highIndex)) + " " + formatNumber(winrates.get(highIndex)) + "%% " + Colors.BLACK + ",08";
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
                if (stat.equals("wins")){
                    statName = "ttwins";
                    title += " Texas Hold'em Tournament Wins ";
                } else if (stat.equals("tourneys")) {
                    statName = "ttplayed";
                    title += " Texas Hold'em Tournaments Played ";
                } else {
                    throw new IllegalArgumentException();
                }

                // Sort based on stat
                Collections.sort(records, Player.getComparator(statName));

                // Add the players in the required range
                for (int ctr = start; ctr < end; ctr++){
                    aRecord = records.get(ctr);
                    list += " #" + (ctr+1) + ": " + Colors.WHITE + ",04 " + formatNoPing(aRecord.getNick()) + " " + formatNumber(aRecord.getInteger(statName)) + " " + Colors.BLACK + ",08";
                }
            }
            
            // Output the title and list
            showMsg(title);
            showMsg(list);
        }
    }
    
    @Override
    public void showStacks() {
        ArrayList<Player> list = new ArrayList<>(joined);
        String msg = Colors.YELLOW + ",01 Stacks: " + Colors.NORMAL + " ";
        Collections.sort(list, Player.getComparator("cash"));
        
        // Add players still in the tournament
        for (Player p : list) {
            if (!p.has("cash") || p.has("quit")) {
                msg += p.getNick(false) + " (" + Colors.RED + formatBold("OUT") + Colors.NORMAL + "), ";
            } else {
                msg += p.getNick(false) + " (" + formatBold("$" + formatNumber(p.getInteger("cash")));
                // Add player stack change
                if (p.getInteger("change") > 0) {
                    msg += "[" + Colors.DARK_GREEN + Colors.BOLD + "$" + formatNumber(p.getInteger("change")) + Colors.NORMAL + "]";
                } else if (p.getInteger("change") < 0) {
                    msg += "[" + Colors.RED + Colors.BOLD + "$" + formatNumber(p.getInteger("change")) + Colors.NORMAL + "]";
                } else {
                    msg += "[" + Colors.BOLD + "$" + formatNumber(p.getInteger("change")) + Colors.NORMAL + "]";
                }
                msg += "), ";
            }
        }
        
        // Add players who are no longer in the tournament
        for (Player p : blacklist) {
            msg += p.getNick(false) + " (" + Colors.RED + formatBold("OUT") + Colors.NORMAL + "), ";
        }
        
        showMsg(msg.substring(0, msg.length()-2));
    }
    
    ///////////////////////////
    //// Formatted strings ////
    ///////////////////////////
    
    @Override
    public String getGameNameStr() {
        return formatBold(getMsg("tt_game_name"));
    }
    
    @Override
    public String getGameRulesStr() {
        if (has("doubleonbankrupt")) {
            return String.format(getMsg("tt_rules_dob"), get("cash"), get("minbet")/2, get("minbet"), get("doubleblinds"), get("minplayers"));
        }
        return String.format(getMsg("tt_rules"), get("cash"), get("minbet")/2, get("minbet"), get("doubleblinds"), get("minplayers"));
    }
    
    @Override
    public String getGameStatsStr() {
        return String.format(getMsg("tt_stats"), getTotalPlayers(), getGameNameStr(), tourneyStats);
    }
}