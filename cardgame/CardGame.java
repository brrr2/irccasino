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
package irccasino.cardgame;

import irccasino.GameManager;
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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Timer;
import org.pircbotx.Channel;
import org.pircbotx.Colors;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.KickEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.NickChangeEvent;
import org.pircbotx.hooks.events.PartEvent;
import org.pircbotx.hooks.events.QuitEvent;

/**
 * Generic class for card games written for irccasino.
 * @author Yizhe Shen
 */
public abstract class CardGame extends ListenerAdapter<PircBotX> {
    
    protected GameManager manager;
    protected Channel channel;
    protected char commandChar;
    // Player lists
    protected ArrayList<Player> joined;
    protected ArrayList<Player> blacklist;
    protected ArrayList<Player> waitlist;
    protected CardDeck deck; //the deck of cards
    protected Player currentPlayer; //stores the player whose turn it is
    protected Timer gameTimer;
    /** INI file settings **/
    protected HashMap<String,Integer> settings;
    // Game properties
    protected int startCount;
    protected long lastPing;
    protected long startTime;
    protected long endTime;
    protected String name;
    protected String iniFile;
    protected String helpFile;
    protected String strFile;
    protected String dbURL;
    protected HashMap<String,String> cmdMap;
    protected HashMap<String,String> opCmdMap;
    protected HashMap<String,String> aliasMap;
    protected HashMap<String,String> msgMap;
    protected ArrayList<String> awayList;
    protected ArrayList<String> notSimpleList;
    // TimerTasks
    protected IdleOutTask idleOutTask;
    protected IdleWarningTask idleWarningTask;
    protected StartRoundTask startRoundTask;
    protected ArrayList<RespawnTask> respawnTasks;

    public CardGame() {
        super();
    }
    
    /**
     * Creates a generic CardGame with a custom INI file.
     * Not to be directly instantiated.
     * 
     * @param parent The bot that uses an instance of this class
     * @param commChar The command char
     * @param gameChannel The IRC channel in which the game is to be run.
     * @param customINI INI file for the game
     */
    public CardGame(GameManager parent, char commChar, Channel gameChannel, String customINI) {
        manager = parent;
        commandChar = commChar;
        channel = gameChannel;
        iniFile = customINI;
        
        initGame();
    }
    
    ////////////////////
    //// IRC events ////
    ////////////////////
    /**
     * Occurs when a message is sent to the game channel.
     * @param event message event
     */
    @Override
    public void onMessage(MessageEvent<PircBotX> event){
        String msg = event.getMessage();
        
        // Parse the message if it is a command
        if (msg.length() > 1 && msg.charAt(0) == commandChar && 
                msg.charAt(1) != ' ' && event.getChannel().equals(channel)){
            StringTokenizer st = new StringTokenizer(msg.substring(1));
            String command = st.nextToken();
            String[] params = new String[st.countTokens()];
            for (int ctr = 0; ctr < params.length; ctr++){
                params[ctr] = st.nextToken();
            }
            
            processCommand(event.getUser(), command, params);
        }
    }
    
    /**
     * Occurs when a user parts the game channel.
     * @param event part event
     */
    @Override
    public void onPart(PartEvent<PircBotX> event){
        if (event.getChannel().equals(channel)){
            processPart(event.getUser());
        }
    }

    /**
     * Occurs when a user disconnects from the IRC network.
     * @param event quit event
     */
    @Override
    public void onQuit(QuitEvent<PircBotX> event){
        processQuit(event.getUser());
    }

    /**
     * Occurs when a user changes nicks.
     * @param event nick change event
     */
    @Override
    public void onNickChange(NickChangeEvent<PircBotX> event){
        processNickChange(event.getUser(), event.getOldNick(), event.getNewNick());
    }
    
    /**
     * Occurs when a user is kicked from the game channel.
     * @param event 
     */
    @Override
    public void onKick(KickEvent<PircBotX> event) {
        if (event.getChannel().equals(channel)) {
            processKick(event.getRecipient());
        }
    }
    
    /////////////////////////////////////////
    //// Methods that process IRC events ////
    /////////////////////////////////////////
    /**
     * Processes commands in the channel where the game is running.
     * 
     * @param user IRC user who issued the command.
     * @param command The command that was issued.
     * @param params A list of parameters that were passed along.
     */
    abstract protected void processCommand(User user, String command, String[] params);
    
    /**
     * Process a user part event in the game channel.
     * @param user 
     */
    protected void processPart(User user) {
        processQuit(user);
    }
    
    /**
     * Processes a user quit event.
     * 
     * @param user The IRC user who has disconnected from the server.
     */
    protected void processQuit(User user){
        String nick = user.getNick();
        if (isWaitlisted(nick)) {
            removeWaitlisted(nick);
            informPlayer(nick, getMsg("leave_waitlist"));
        } else if (!isJoined(nick)){
            // Do nothing
        } else if (!isInProgress()) {
            removeJoined(nick);
            showMsg(getMsg("unjoin"), formatBold(nick), joined.size());
        } else {
            leave(nick);
        }
    }
    
    /**
     * Process a user change nick event in the game channel.
     * 
     * @param user The IRC user who has changed nicks.
     * @param oldNick The old nick of the user.
     * @param newNick The new nick of the user.
     */
    protected void processNickChange(User user, String oldNick, String newNick){
        String host = user.getHostmask();
        if (isWaitlisted(oldNick)) {
            informPlayer(newNick, getMsg("nick_change"));
            informPlayer(newNick, getMsg("leave_waitlist"));
            removeWaitlisted(oldNick);
            join(newNick, host);
        } else if (!isJoined(oldNick)){
            // Do nothing
        } else if (!isInProgress()) {
            informPlayer(newNick, getMsg("nick_change"));
            removeJoined(oldNick);
            showMsg(getMsg("unjoin"), formatBold(oldNick), joined.size());
            join(newNick, host);
        } else {
            informPlayer(newNick, getMsg("nick_change"));
            leave(oldNick);
            join(newNick, host);
        }
    }
    
    /**
     * Processes a kick event in the game channel.
     * @param recip 
     */
    protected void processKick(User recip) {
        processQuit(recip);
    }
    
    /////////////////////////
    //// Command methods ////
    /////////////////////////
    
    /**
     * Attempts to add a player to the game.
     * @param nick the player's nick
     * @param host the player's host
     */
    protected void join(String nick, String host) {
        CardGame game = manager.getGame(nick);
        if (joined.size() == get("maxplayers")){
            informPlayer(nick, getMsg("max_players"));
        } else if (isJoined(nick)) {
            informPlayer(nick, getMsg("is_joined"));
        } else if (isBlacklisted(nick)) {
            Player p = findBlacklisted(nick);
            long timeLeft = p.getLong("respawn") - System.currentTimeMillis()/1000;
            informPlayer(nick, getMsg("on_blacklist_time"), timeLeft/60, timeLeft % 60);
        } else if (manager.isBlacklisted(nick)) {
            informPlayer(nick, getMsg("on_blacklist"));
        } else if (game != null) {
            informPlayer(nick, getMsg("is_joined_other"), game.getGameNameStr(), game.getChannel().getName());
        } else if (isWaitlisted(nick)) {
            informPlayer(nick, getMsg("on_waitlist"));
        } else if (isInProgress()) {
            addWaitlistPlayer(nick, host);
        } else {
            addPlayer(nick, host);
        }
    }
    
    /**
     * Attempts to remove a player from the game.
     * @param nick the player's nick
     * @param params
     */
    protected void leave(String nick, String[] params) {
        if (isWaitlisted(nick)) {
            removeWaitlisted(nick);
            informPlayer(nick, getMsg("leave_waitlist"));
        } else if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else {
            leave(nick);
        }
    }
    
    /**
     * Allows the user to automatically leave after the end of the round.
     * @param nick
     * @param params 
     */
    protected void last(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else {
            Player p = findJoined(nick);
            p.put("last", true);
            informPlayer(p.getNick(), getMsg("remove_end_round"));
        }
    }
    
    /**
     * Cancels any remaining auto-starts.
     * @param nick 
     * @param params 
     */
    protected void stop(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else if (startCount == 0) {
            informPlayer(nick, getMsg("stop_no_autostarts"));
        } else {
            startCount = 0;
            showMsg(getMsg("stop"));
        }
    }
    
    /**
     * Displays a player's stack.
     * @param nick 
     * @param params 
     */
    protected void cash(String nick, String[] params) {
        if (params.length > 0){
            showPlayerCash(params[0]);
        } else {
            showPlayerCash(nick);
        }
    }
    
    /**
     * Displays a player's net cash.
     * @param nick
     * @param params 
     */
    protected void netcash(String nick, String[] params) {
        if (params.length > 0){
            showPlayerNetCash(params[0]);
        } else {
            showPlayerNetCash(nick);
        }
    }
    
    /**
     * Displays a player's bank amount.
     * @param nick
     * @param params 
     */
    protected void bank(String nick, String[] params) {
        if (params.length > 0){
            showPlayerBank(params[0]);
        } else {
            showPlayerBank(nick);
        }
    }
    
    /**
     * Displays a player's number of bankruptcies.
     * @param nick
     * @param params 
     */
    protected void bankrupts(String nick, String[] params) {
        if (params.length > 0){
            showPlayerBankrupts(params[0]);
        } else {
            showPlayerBankrupts(nick);
        }
    }
    
    /**
     * Displays a player's winnings in the current game.
     * @param nick
     * @param params 
     */
    protected void winnings(String nick, String[] params) {
        if (params.length > 0){
            showPlayerWinnings(params[0]);
        } else {
            showPlayerWinnings(nick);
        }
    }
    
    /**
     * Displays a player's win rate in the current game.
     * @param nick
     * @param params 
     */
    protected void winrate(String nick, String[] params) {
        if (params.length > 0){
            showPlayerWinRate(params[0]);
        } else {
            showPlayerWinRate(nick);
        }
    }
    
    /**
     * Displays a player's number of rounds played in the current game.
     * @param nick
     * @param params 
     */
    protected void rounds(String nick, String[] params) {
        if (params.length > 0){
            showPlayerRounds(params[0]);
        } else {
            showPlayerRounds(nick);
        }
    }
    
    /**
     * Displays a player's full stats for the current game.
     * @param nick
     * @param params 
     */
    protected void player(String nick, String[] params) {
        if (params.length > 0){
            showPlayerAllStats(params[0]);
        } else {
            showPlayerAllStats(nick);
        }
    }
    
    /**
     * Deposits the specified amount to the player's bank.
     * @param nick
     * @param params 
     */
    protected void deposit(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else {
            try {
                transfer(nick, Integer.parseInt(params[0]));
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Deposits/withdraws the amount over/under 1000 to/from the player's bank.
     * @param nick
     * @param params
     */
    protected void rathole(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            Player p = findJoined(nick);
            transfer(nick, p.getInteger("cash") - 1000);
        }
    }    

    /**
     * Withdraws the specified amount from the player's bank.
     * @param nick
     * @param params 
     */
    protected void withdraw(String nick, String[] params) {
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else {
            try {
                transfer(nick, -Integer.parseInt(params[0]));
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Displays the players on the waiting list for the current game.
     * @param nick
     * @param params 
     */
    protected void waitlist(String nick, String[] params) {
        showMsg(getMsg("waitlist"), getPlayerListString(waitlist));
    }
    
    /**
     * Displays the bankrupt players for the current game.
     * @param nick
     * @param params 
     */
    protected void blacklist(String nick, String[] params) {
        showMsg(getMsg("blacklist"), getPlayerListString(blacklist));
    }
    
    /**
     * Displays a player's rank in a stat.
     * @param nick
     * @param params 
     */
    protected void rank(String nick, String[] params) {
        if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
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
            showPlayerRank(nick, "cash");
        }
    }
    
    /**
     * Displays the top players in a stat.
     * @param nick
     * @param params 
     */
    protected void top(String nick, String[] params) {
        if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (params.length > 1){
            try {
                showTopPlayers(params[1], Integer.parseInt(params[0]));
            } catch (IllegalArgumentException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        } else if (params.length == 1){
            try {
                showTopPlayers("cash", Integer.parseInt(params[0]));
            } catch (IllegalArgumentException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        } else {
            showTopPlayers("cash", 5);
        }
    }
    
    /**
     * Adds the user to the away list. 
     * @param nick
     * @param params
     */
    public void away(String nick, String[] params) {
        User user = findUser(nick);
        if (awayList.contains(user.getHostmask())) {
            informPlayer(nick, "You are already marked as away!");
        } else {
            awayList.add(user.getHostmask());
            saveHostList("away.txt", awayList);
            informPlayer(nick, "You are now marked as away.");
        }
    }
    
    /**
     * Removes the user from the away list. 
     * @param nick
     * @param params
     */
    public void back(String nick, String[] params) {
        User user = findUser(nick);
        if (awayList.contains(user.getHostmask())){
            awayList.remove(user.getHostmask());
            saveHostList("away.txt", awayList);
            informPlayer(nick, "You are no longer marked as away.");
        } else {
            informPlayer(nick, "You are not marked as away!");
        }
    }
    
    /**
     * Toggles a player's "simple" status.
     * Players with "simple" set to true will have game information sent via
     * /notice. Players with "simple" set to false will have game information 
     * sent via /msg.
     * @param nick
     * @param params 
     */
    protected void simple(String nick, String[] params) {
        User user = findUser(nick);
        if (notSimpleList.contains(user.getHostmask())) {
            notSimpleList.remove(user.getHostmask());
            informPlayer(nick, "Game info will now be noticed to you.");
        } else {
            notSimpleList.add(user.getHostmask());
            informPlayer(nick, "Game info will now be messaged to you.");
        }
        saveHostList("simple.txt", notSimpleList);
    }
    
    /**
     * Displays the stats for the current game.
     * @param nick
     * @param params 
     */
    protected void stats(String nick, String[] params) {
        if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            showMsg(getGameStatsStr());
        }
    }
    
    /**
     * Displays the rules for the current game.
     * @param nick
     * @param params 
     */
    protected void grules(String nick, String[] params) {
        informPlayer(nick, getGameRulesStr());
    }
    
    /**
     * Displays a help message.
     * @param nick
     * @param params 
     */
    protected void ghelp(String nick, String[] params) {
        if (params.length == 0){
            informPlayer(nick, getMsg("game_help"), commandChar, commandChar, commandChar);
        } else {
            informPlayer(nick, getCommandHelp(params[0].toLowerCase()));
        }
    }
    
    /**
     * Displays the commands available for this game.
     * @param user
     * @param nick
     * @param params 
     */
    protected void gcommands(User user, String nick, String[] params) {
        informPlayer(nick, getGameNameStr() + " commands:");
        informPlayer(nick, getCommandsStr());
        if (channel.isOp(user)) {
            informPlayer(nick, getGameNameStr() + " Op commands:");
            informPlayer(nick, getOpCommandsStr());
        }
    }
    
    /**
     * Displays the name of the current game.
     * @param nick
     * @param params 
     */
    protected void game(String nick, String[] params) {
        showMsg(getMsg("game_name"), getGameNameStr());
    }
    
    /**
     * Displays a list of users in the game channel, excluding users who are 
     * in the awayList.
     * @param nick
     * @param params 
     */
    protected void ping(String nick, String[] params) {
        // Check for rate-limit
        long timeLeft = (get("ping") - (System.currentTimeMillis() / 1000 - lastPing));
        if (lastPing != 0 && timeLeft > 0) {
            informPlayer(nick, "This command is rate-limited. Please wait %02d:%02d to use it again.", timeLeft / 60, timeLeft % 60);
        } else if (isInProgress()) {
            informPlayer(nick, "This command cannot be used at this time.");
        } else {
            // Grab the users in the channel
            User tUser;
            String outStr = "Ping: ";
            Iterator<User> it = channel.getUsers().iterator();
            while(it.hasNext()){
                tUser = it.next();
                if (!awayList.contains(tUser.getHostmask()) && !isJoined(tUser.getNick())){
                    outStr += tUser.getNick()+", ";
                }
            }
            showMsg(outStr.substring(0, outStr.length() - 2));
            lastPing = System.currentTimeMillis() / 1000;
        }
    }
    
    /**
     * Attempts to force add a player to the game.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fjoin(User user, String nick, String[] params) {
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
                        if (isWaitlisted(fNick)) {
                            informPlayer(nick, getMsg("on_waitlist_nick"), fNick);
                        } else {
                            addWaitlistPlayer(u.getNick(), u.getHostmask());
                        }
                    } else {
                        addPlayer(u.getNick(), u.getHostmask());
                    }
                    return;
                }
            }
            informPlayer(nick, getMsg("nick_not_found"), fNick);
        }
    }
    
    /**
     * Attempts to force a player to leave the game.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fleave(User user, String nick, String[] params) {
        String fNick = params[0];
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else if (isWaitlisted(fNick)) {
            removeWaitlisted(fNick);
            informPlayer(nick, getMsg("leave_waitlist"));
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
    
    /**
     * 
     * @param user
     * @param nick
     * @param params 
     */
    protected void flast(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (params.length < 1) {
            informPlayer(nick, getMsg("no_parameter"));
        } else if (!isJoined(params[0])) {
            informPlayer(nick, params[0] + " is not currently joined!");
        } else if (!isInProgress()) {
            informPlayer(nick, getMsg("no_start"));
        } else {
            Player p = findJoined(params[0]);
            p.put("quit", true);
            informPlayer(nick, getMsg("remove_end_round_nick"), params[0]);
        }
    }
    
    /**
     * Attempts to force a player to make a deposit.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fdeposit(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (params.length < 2) {
            informPlayer(nick, getMsg("no_parameter"));
        } else if (!isJoined(params[0])) {
            informPlayer(nick, params[0] + " is not currently joined!");
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            try {
                transfer(params[0], Integer.parseInt(params[1]));
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Attempts to force a player to make a withdrawal.
     * @param user
     * @param nick
     * @param params 
     */
    protected void fwithdraw(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (params.length < 2) {
            informPlayer(nick, getMsg("no_parameter"));
        } else if (!isJoined(params[0])) {
            informPlayer(nick, params[0] + " is not currently joined!");
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            try {
                transfer(params[0], -Integer.parseInt(params[1]));
            } catch (NumberFormatException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Attempts to reveal the specified top number of cards in the card deck.
     * @param user
     * @param nick
     * @param params 
     */
    protected void cards(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));    
        } else if (deck.getNumberCards() == 0) {
            informPlayer(nick, "Empty!");
        } else {
            try {
                infoDeckCards(nick, 'c', Integer.parseInt(params[0]));
            } catch (IllegalArgumentException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Attempts to reveal the specified top number of cards in the discards.
     * @param user
     * @param nick
     * @param params 
     */
    protected void discards(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (params.length < 1){
            informPlayer(nick, getMsg("no_parameter"));
        } else if (deck.getNumberDiscards() == 0) {
            informPlayer(nick, "Empty!");
        } else {
            try {
                infoDeckCards(nick, 'd', Integer.parseInt(params[0]));
            } catch (IllegalArgumentException e) {
                informPlayer(nick, getMsg("bad_parameter"));
            }
        }
    }
    
    /**
     * Reveals the settings available for the current game.
     * @param user
     * @param nick
     * @param params 
     */
    protected void settings(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            informPlayer(nick, getGameNameStr() + " settings:");
            informPlayer(nick, getSettingsStr());
        }
    }
    
    /**
     * Attempts to update the specified setting with the specified value.
     * @param user
     * @param nick
     * @param params 
     */
    protected void set(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
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
    
    /**
     * Attempts to get the value of the specified setting.
     * @param user
     * @param nick
     * @param params 
     */
    protected void get(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (isInProgress()) {
            informPlayer(nick, getMsg("wait_round_end"));
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
    
    /**
     * Erases all hosts from away.txt.
     * @param user 
     * @param nick 
     * @param params 
     */
    public void resetaway(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else {
            awayList.clear();
            saveHostList("away.txt", awayList);
            informPlayer(nick, "The away list has been reset.");
        }
    }
    
    /**
     * Erases all hosts from simple.txt.
     * @param user 
     * @param nick 
     * @param params 
     */
    public void resetsimple(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else {
            notSimpleList.clear();
            saveHostList("simple.txt", notSimpleList);
            informPlayer(nick, "The simple list has been reset.");
        }
    }
    
    /**
     * Removes players who haven't played a round of any game from players.txt.
     * @param user
     * @param nick
     * @param params 
     */
    public void trim(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (manager.gamesInProgress()) {
            informPlayer(nick, getMsg("no_trim"));
        } else {
            ArrayList<Player> records = loadPlayerFile();
            if (records != null) {
                ArrayList<Player> newRecords = new ArrayList<>();
                for (Player record : records) {
                    if (record.has("bjrounds") || record.has("tprounds") || record.has("ttplayed")) {
                        newRecords.add(record);
                    }
                }
                savePlayerFile(newRecords);
                showMsg("Player data has been trimmed.");
            }
        }
    }
    
    /**
     * Performs an SQL query to the stats DB and outputs a result string.
     * @param user
     * @param nick
     * @param params 
     */
    public void query(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else {
            try (Connection conn = DriverManager.getConnection(dbURL)) {
                // Rebuild query
                String sql = "";
                for (String s : params) {
                    sql += s + " ";
                }
                
                // Attempt query and output results to channel
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ResultSet rs = ps.executeQuery();
                    if (rs.isBeforeFirst()) {
                        ResultSetMetaData rsmd = rs.getMetaData();
                        String output = "";
                        int numCol = rsmd.getColumnCount();
                        
                        // Append field names
                        output += "Fields {";
                        for (int ctr = 1; ctr <= numCol; ctr++) {
                            output += rsmd.getColumnName(ctr) + ":";
                        }
                        output = output.substring(0, output.length() - 1) + "}, ";
                        
                        // Append records
                        int row = 1;
                        while(rs.next()) {
                            output += "R" + row++ + " {";
                            for (int ctr = 1; ctr <= numCol; ctr++) {
                                output += rs.getObject(ctr) + ":";
                            }
                            output = output.substring(0, output.length() - 1) + "}, ";
                        }
                        showMsg(output.substring(0, Math.min(300, output.length() - 2)));
                    } else {
                        showMsg("SQL query produced no results.");
                    }
                }
            } catch (SQLException ex) {
                showMsg("SQL Error: " + ex.getMessage());
                manager.log("SQL Error: " + ex.getMessage());
            }
        }
    }
    
    /**
     * Migrates players.txt entries into stats.sqlite3.
     * @param user
     * @param nick
     * @param params 
     */
    public void migrate(User user, String nick, String[] params) {
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (manager.gamesInProgress()) {
            informPlayer(nick, getMsg("no_migrate"));
        } else {
            showMsg("Performing migration from players.txt to stats.sqlite3. Please wait...");
            
            ArrayList<Player> records = loadPlayerFile();
            int playerID;
            String sql;
            
            try (Connection conn = DriverManager.getConnection(dbURL)) {
                conn.setAutoCommit(false);
                
                // Iterate over records
                for (Player record : records) {
                    playerID = -1;
                    
                    // Add new record if not found in Player table
                    sql = "INSERT INTO Player (nick, time_created) VALUES(?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, record.getString("nick"));
                        ps.setLong(2, System.currentTimeMillis() / 1000);
                        ps.executeUpdate();
                        playerID = ps.getGeneratedKeys().getInt(1);
                    }

                    if (playerID != -1) {
                        // Attempt to add new record in Purse table
                        sql = "INSERT INTO Purse (player_id, cash, bank, bankrupts) " +
                              "VALUES(?, ?, ?, ?)";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setInt(1, playerID);
                            ps.setInt(2, record.getInteger("cash"));
                            ps.setInt(3, record.getInteger("bank"));
                            ps.setInt(4, record.getInteger("bankrupts"));
                            ps.executeUpdate();
                        }

                        // Attempt to add new record in TPPlayerStat table
                        sql = "INSERT INTO TPPlayerStat (player_id, rounds, winnings, idles) " +
                              "VALUES(?, ?, ?, ?)";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setInt(1, playerID);
                            ps.setInt(2, record.getInteger("tprounds"));
                            ps.setInt(3, record.getInteger("tpwinnings"));
                            ps.setInt(4, 0);
                            ps.executeUpdate();
                        }

                        // Attempt to add new record in BJPlayerStat table
                        sql = "INSERT INTO BJPlayerStat (player_id, rounds, winnings, idles) " +
                              "VALUES(?, ?, ?, ?)";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setInt(1, playerID);
                            ps.setInt(2, record.getInteger("bjrounds"));
                            ps.setInt(3, record.getInteger("bjwinnings"));
                            ps.setInt(4, 0);
                            ps.executeUpdate();
                        }

                        // Attempt to add new record in TPPlayerStat table
                        sql = "INSERT INTO TTPlayerStat (player_id, tourneys, points, idles) " +
                              "VALUES(?, ?, ?, ?)";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setInt(1, playerID);
                            ps.setInt(2, record.getInteger("ttplayed"));
                            ps.setInt(3, record.getInteger("ttwins"));
                            ps.setInt(4, 0);
                            ps.executeUpdate();
                        }
                    }
                }
                
                // Migrate house data for Blackjack
                try (BufferedReader in = new BufferedReader(new FileReader("housestats.txt"))) {
                    String str;
                    int decks, rounds, winnings;
                    StringTokenizer st;
                    while (in.ready()) {
                        str = in.readLine();
                        if (str.startsWith("#blackjack")) {
                            while (in.ready()) {
                                str = in.readLine();
                                if (str.startsWith("#")) {
                                    break;
                                }
                                st = new StringTokenizer(str);
                                decks = Integer.parseInt(st.nextToken());
                                rounds = Integer.parseInt(st.nextToken());
                                winnings = Integer.parseInt(st.nextToken());
                                sql = "INSERT INTO BJHouse (shoe_size, rounds, winnings) VALUES(?, ?, ?)";
                                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                                    ps.setInt(1, decks);
                                    ps.setInt(2, rounds);
                                    ps.setInt(3, winnings);
                                    ps.executeUpdate();
                                }
                            }
                            break;
                        }
                    }
                } catch (IOException e) {
                    manager.log("housestats.txt not found! No house stats migrated...");
                }
                
                conn.commit();
                showMsg("Migration complete.");
            } catch (SQLException ex) {
                manager.log("SQL Error: " + ex.getMessage());
                showMsg("Migration aborted.");
            }
        }
    }
    
    //////////////////////////
    //// Accessor methods ////
    //////////////////////////
    /**
     * Returns the game channel.
     * @return the game channel
     */
    public Channel getChannel(){
        return channel;
    }
    
    /**
     * Public accessor required for GameManager.
     * @return true if a game is in progress
     */
    abstract public boolean isInProgress();
    
    //////////////////////////////////
    ////  Game management methods ////
    //////////////////////////////////
    /**
     * Starts a new round of the game.
     */
    abstract protected void startRound();
    
    /**
     * Progresses the game during a round.
     */
    abstract protected void continueRound();
    
    /**
     * Finishes a round of the game
     */
    abstract protected void endRound();
    
    /**
     * Terminates the game.
     */
    abstract public void endGame();
    
    /**
     * Resets the game, usually at the end of a round.
     */
    abstract protected void resetGame();
    
    /**
     * Saves the INI file associated with the game instance.
     */
    abstract protected void saveIniFile();
    
    /**
     * Initializes game settings.
     */
    abstract protected void initSettings();
    
    /**
     * Additional custom initializations for individual games.
     */
    abstract protected void initCustom();
    
    /**
     * Initializes the game.
     */
    protected final void initGame() {
        joined = new ArrayList<>();
        blacklist = new ArrayList<>();
        waitlist = new ArrayList<>();
        gameTimer = new Timer("Game Timer");
        settings = new HashMap<>();
        cmdMap = new HashMap<>();
        opCmdMap = new HashMap<>();
        aliasMap = new HashMap<>();
        msgMap = new HashMap<>();
        awayList = new ArrayList<>();
        notSimpleList = new ArrayList<>();
        respawnTasks = new ArrayList<>();
        strFile = "strlib.txt";
        dbURL = "jdbc:sqlite:stats.sqlite3";
        
        // Load SQLite JDBC driver
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {}
        
        loadStrLib(strFile);
        loadHostList("away.txt", awayList);
        loadHostList("simple.txt", notSimpleList);
        initDB();
        initCustom();
    }
    
    /**
     * Loads the database and creates any necessary tables.
     */
    protected final void initDB() {
        try (Connection conn = DriverManager.getConnection(dbURL)) {
            conn.setAutoCommit(false);
            
            // Create tables if necessary
            try (Statement s = conn.createStatement()) {
                // Player table
                s.execute( "CREATE TABLE IF NOT EXISTS Player (" +
                           "id INTEGER PRIMARY KEY, nick TEXT, " +
                           "time_created INTEGER, UNIQUE(nick))");

                // Purse table
                s.execute( "CREATE TABLE IF NOT EXISTS Purse (" +
                           "player_id INTEGER, cash INTEGER, " +
                           "bank INTEGER, bankrupts INTEGER, " +
                           "UNIQUE(player_id), " +
                           "FOREIGN KEY(player_id) REFERENCES Player(id))");
                
                // Banking table
                s.execute( "CREATE TABLE IF NOT EXISTS Banking (" +
                           "id INTEGER PRIMARY KEY, player_id INTEGER, " + 
                           "transaction_time INTEGER, cash_change INTEGER, " +
                           "cash INTEGER, bank INTEGER, " +
                           "FOREIGN KEY(player_id) REFERENCES Player(id))");
                
                // BJPlayerStat table
                s.execute( "CREATE TABLE IF NOT EXISTS BJPlayerStat (" +
                           "player_id INTEGER, rounds INTEGER, " +
                           "winnings INTEGER, idles INTEGER, " +
                           "UNIQUE(player_id), " +
                           "FOREIGN KEY(player_id) REFERENCES Player(id))");
                
                // BJRound table
                s.execute( "CREATE TABLE IF NOT EXISTS BJRound (" +
                           "id INTEGER PRIMARY KEY, " +
                           "start_time INTEGER, end_time INTEGER, " +
                           "channel TEXT, shoe_size INTEGER, " +
                           "num_cards_left INTEGER, " +
                           "FOREIGN KEY(shoe_size) REFERENCES BJHouse(shoe_size))");
                
                // BJHand table
                s.execute( "CREATE TABLE IF NOT EXISTS BJHand (" +
                           "id INTEGER PRIMARY KEY, " +
                           "round_id INTEGER, hand TEXT, " +
                           "FOREIGN KEY(round_id) REFERENCES BJRound(id))");
                
                // BJPlayerHand table
                s.execute( "CREATE TABLE IF NOT EXISTS BJPlayerHand (" +
                           "player_id INTEGER, hand_id INTEGER, " +
                           "bet INTEGER, split BOOLEAN, surrender BOOLEAN, " +
                           "doubledown BOOLEAN, result INTEGER, " +
                           "UNIQUE(player_id, hand_id), " +
                           "FOREIGN KEY(player_id) REFERENCES Player(id), " +
                           "FOREIGN KEY(hand_id) REFERENCES BJHand(id))");
                
                // BJPlayerInsurance table
                s.execute( "CREATE TABLE IF NOT EXISTS BJPlayerInsurance (" +
                           "player_id INTEGER, round_id INTEGER, " +
                           "bet INTEGER, result BOOLEAN, " +
                           "UNIQUE(player_id, round_id), " +
                           "FOREIGN KEY(player_id) REFERENCES Player(id), " +
                           "FOREIGN KEY(round_id) REFERENCES BJRound(id))");
                
                // BJPlayerChange table
                s.execute( "CREATE TABLE IF NOT EXISTS BJPlayerChange (" +
                           "player_id INTEGER, round_id INTEGER, " +
                           "change INTEGER, cash INTEGER, " +
                           "UNIQUE(player_id, round_id), " +
                           "FOREIGN KEY(player_id) REFERENCES Player(id), " +
                           "FOREIGN KEY(round_id) REFERENCES BJRound(id))");
                
                // BJPlayerIdle table
                s.execute( "CREATE TABLE IF NOT EXISTS BJPlayerIdle (" +
                           "player_id INTEGER, round_id INTEGER, " +
                           "idle_limit INTEGER, idle_warning INTEGER, " +
                           "UNIQUE(player_id, round_id), " +
                           "FOREIGN KEY(player_id) REFERENCES Player(id), " +
                           "FOREIGN KEY(round_id) REFERENCES BJRound(id))");
                
                // BJHouseStat table
                s.execute( "CREATE TABLE IF NOT EXISTS BJHouse (" +
                           "shoe_size INTEGER, rounds INTEGER, " +
                           "winnings INTEGER, UNIQUE(shoe_size))");
                
                // TPPlayerStat table
                s.execute( "CREATE TABLE IF NOT EXISTS TPPlayerStat (" +
                           "player_id INTEGER, rounds INTEGER, " +
                           "winnings INTEGER, idles INTEGER, " +
                           "UNIQUE(player_id), " +
                           "FOREIGN KEY(player_id) REFERENCES Player(id))");
                
                // TPRound table
                s.execute( "CREATE TABLE IF NOT EXISTS TPRound (" +
                           "id INTEGER PRIMARY KEY, start_time INTEGER, " +
                           "end_time INTEGER, channel TEXT, community TEXT)");
                
                // TPPot table
                s.execute( "CREATE TABLE IF NOT EXISTS TPPot (" +
                           "pot_id INTEGER PRIMARY KEY, " +
                           "round_id INTEGER, amount INTEGER, " +
                           "FOREIGN KEY(round_id) REFERENCES TPRound(id))");
                
                // TPPlayerPot table
                s.execute( "CREATE TABLE IF NOT EXISTS TPPlayerPot (" +
                           "player_id INTEGER, pot_id INTEGER, " +
                           "contribution INTEGER, result BOOLEAN, " +
                           "UNIQUE(player_id, pot_ID), " + 
                           "FOREIGN KEY(player_id) REFERENCES Player(id), " +
                           "FOREIGN KEY(pot_id) REFERENCES TPPot(id))");
                
                // TPPlayerChange table
                s.execute( "CREATE TABLE IF NOT EXISTS TPPlayerChange (" +
                           "player_id INTEGER, round_id INTEGER, " +
                           "change INTEGER, cash INTEGER, " + 
                           "UNIQUE(player_id, round_id), " +
                           "FOREIGN KEY(player_id) REFERENCES Player(id), " +
                           "FOREIGN KEY(round_id) REFERENCES TPRound(id))");
                
                // TPHand table
                s.execute( "CREATE TABLE IF NOT EXISTS TPHand (" +
                           "id INTEGER PRIMARY KEY, " + 
                           "round_id INTEGER, hand TEXT, " +
                           "FOREIGN KEY(round_id) REFERENCES TPRound(id))");
                
                // TPPlayerHand table
                s.execute( "CREATE TABLE IF NOT EXISTS TPPlayerHand (" +
                           "player_id INTEGER, hand_id INTEGER, " +
                           "fold BOOLEAN, allin BOOLEAN, " +
                           "UNIQUE(player_id, hand_id), " +
                           "FOREIGN KEY(player_id) REFERENCES Player(id), " +
                           "FOREIGN KEY(hand_id) REFERENCES TPHand(id))");
                
                // TPPlayerIdle table
                s.execute( "CREATE TABLE IF NOT EXISTS TPPlayerIdle (" +
                           "player_id INTEGER, round_id INTEGER, " +
                           "idle_limit INTEGER, idle_warning INTEGER, " +
                           "UNIQUE(player_id, round_id), " +
                           "FOREIGN KEY(player_id) REFERENCES Player(id), " +
                           "FOREIGN KEY(round_id) REFERENCES TPRound(id))");
                
                // TTPlayerStat table
                s.execute( "CREATE TABLE IF NOT EXISTS TTPlayerStat (" +
                           "player_id INTEGER, tourneys INTEGER, " +
                           "points INTEGER, idles INTEGER, " +
                           "UNIQUE(player_id), " +
                           "FOREIGN KEY(player_id) REFERENCES Player(id))");
                
                // TTTourney table
                s.execute( "CREATE TABLE IF NOT EXISTS TTTourney (" +
                           "id INTEGER PRIMARY KEY, start_time INTEGER, " +
                           "end_time INTEGER, channel TEXT, rounds INTEGER)");
                
                // TTPlayerTourney table
                s.execute( "CREATE TABLE IF NOT EXISTS TTPlayerTourney (" +
                           "player_id INTEGER, tourney_id INTEGER, " +
                           "result BOOLEAN, UNIQUE(player_id, tourney_id), " +
                           "FOREIGN KEY(player_id) REFERENCES Player(id), " +
                           "FOREIGN KEY(tourney_id) REFERENCES TTTourney(id))");
                
                // TTPlayerIdle table
                s.execute( "CREATE TABLE IF NOT EXISTS TTPlayerIdle (" +
                           "player_id INTEGER, tourney_id INTEGER, " +
                           "idle_limit INTEGER, idle_warning INTEGER, " +
                           "UNIQUE(player_id, tourney_id), " +
                           "FOREIGN KEY(player_id) REFERENCES Player(id), " +
                           "FOREIGN KEY(tourney_id) REFERENCES TTTourney(id))");
                
                conn.commit();
            }
            
            logDBWarning(conn.getWarnings());
        } catch (SQLException ex) {
            manager.log("SQL Error: " + ex.getMessage());
        }
    }
    
    /**
     * Overwrites the value of a setting, if it exists.
     * @param setting setting name
     * @param value new value
     * @throws IllegalArgumentException bad setting
     */
    protected void set(String setting, int value) throws IllegalArgumentException {
        if (!settings.containsKey(setting)) {
            throw new IllegalArgumentException();
        }
        settings.put(setting, value);
    }
    
    /**
     * Retrieves the value of a setting, if it exists.
     * @param setting setting name
     * @return the setting value
     * @throws IllegalArgumentException bad setting
     */
    protected int get(String setting) throws IllegalArgumentException {
        if (!settings.containsKey(setting)){
            throw new IllegalArgumentException();
        }
        return settings.get(setting);
    }
    
    /**
     * Returns whether or not a setting has a value greater than 0.
     * @param setting setting name
     * @return true if value is greater than 0
     * @throws IllegalArgumentException bad setting
     */
    public boolean has(String setting) throws IllegalArgumentException {
        if (!settings.containsKey(setting)){
            throw new IllegalArgumentException();
        }
        return get(setting) > 0;
    }
    
    /**
     * Loads the settings from the INI file.
     */
    protected void loadIni() {
        try (BufferedReader in = new BufferedReader(new FileReader(iniFile))) {
            String str;
            StringTokenizer st;
            while (in.ready()) {
                str = in.readLine();
                if (!str.startsWith("#") && str.contains("=")) {
                    st = new StringTokenizer(str, "=");
                    set(st.nextToken(), Integer.parseInt(st.nextToken()));
                }
            }
        } catch (IOException e) {
            /* load defaults if INI file is not found */
            manager.log(iniFile + " not found! Creating new " + iniFile + "...");
            saveIniFile();
        }
    }
    
    /**
     * Loads data from strlib file.
     * @param file file path
     */
    protected final void loadStrLib(String file) {
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            StringTokenizer st;
            String str;
            while (in.ready()){
                // Replace all unicode
                str = in.readLine().replaceAll("u0002", Colors.BOLD);
                // Skips all lines that begin with #
                if (!str.startsWith("#") && str.contains("=")) {
                    st = new StringTokenizer(str, "=");
                    msgMap.put(st.nextToken(), st.nextToken());
                }
            }
        } catch (IOException e) {
            manager.log("Error reading from " + file + "!");
        }
    }
    
    /**
     * Loads data from help file.
     * @param file file path
     */
    protected final void loadHelp(String file) {
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String[] st, st2;
            String str, cmd, params, alias, def, line;
            while (in.ready()){
                str = in.readLine();
                // Skips all lines that begin with #
                if (!str.startsWith("#") && str.contains("|")) {
                    st = str.split("\\|");
                    
                    // Command name
                    if (st[0].equals("")) {
                        cmd = "---";
                    } else {
                        cmd = st[0];
                    }
                    
                    // Parameters
                    if (st[1].equals("")) {
                        params = "---";
                    } else {
                        params = st[1];
                    }
                    
                    // Aliases
                    if (st[2].equals("")) {
                        alias = "---";
                    } else {
                        alias = st[2];
                        st2 = st[2].split(",");
                        for (String a : st2) {
                            aliasMap.put(a, cmd);
                        }
                    }
                    
                    // Definition
                    if (st[3].equals("")) {
                        def = "---";
                    } else {
                        def = st[3];
                    }
                    
                    line = String.format(getMsg("help_def"), cmd, params, alias, def);
                    if (def.contains("Op command")) {
                        opCmdMap.put(cmd, line);
                    } else {
                        cmdMap.put(cmd, line);
                    }
                }
            }
        } catch (IOException e) {
            manager.log("Error reading from " + file + "!");
        }
    }
    
    /**
     * Processes a player's departure from the game.
     * @param nick the player's nick
     */
    abstract protected void leave(String nick);
    
    /**
     * Attempts to remove a player from the game.
     * @param p the player
     */
    protected void leave(Player p){
        leave(p.getNick());
    }
    
    /**
     * Moves players on the waitlist to the joined list and clears the waitlist.
     */
    protected void mergeWaitlist(){
        for (Player p : waitlist) {
            addPlayer(p);
        }
        waitlist.clear();
    }
    
    /**
     * Sets a RespawnTask for a player who has gone bankrupt.
     * @param p the bankrupt player
     */
    protected void setRespawnTask(Player p) {
        // Calculate extra time penalty for players with debt
        int penalty = get("respawn") + Math.max(-1 * p.getInteger("bank")/1000 * 60, 0);
        informPlayer(p.getNick(), getMsg("bankrupt_info"), penalty/60, penalty%60);
        p.put("respawn", System.currentTimeMillis()/1000 + penalty);
        
        // TimerTasks are scheduled in milliseconds
        RespawnTask task = new RespawnTask(p, this);
        gameTimer.schedule(task, penalty*1000);
        respawnTasks.add(task);
    }
    
    /**
     * Cancels all RespawnTasks.
     */
    protected void cancelRespawnTasks() {
        for (RespawnTask task : respawnTasks) {
            task.cancel();
        }
        respawnTasks.clear();
        gameTimer.purge();
        // Fast-track loans
        for (Player p : blacklist) {
            p.put("cash", get("cash"));
            p.add("bank", -get("cash"));
            p.put("transaction", get("cash"));
            saveDBPlayerData(p);
            saveDBPlayerBanking(p);
        }
    }
    
    /**
     * Accessor for the respawnTasks ArrayList.
     * @return respawnTasks ArrayList
     */
    protected ArrayList<RespawnTask> getRespawnTasks() {
        return respawnTasks;
    }
    
    /**
     * Schedules a new startRoundTask.
     */
    protected void setStartRoundTask(){
        startRoundTask = new StartRoundTask(this);
        gameTimer.schedule(startRoundTask, get("startwait") * 1000);
    }
    
    /**
     * Cancels the any scheduled startRoundTask.
     */
    protected void cancelStartRoundTask(){
        if (startRoundTask != null){
            startRoundTask.cancel();
            gameTimer.purge();
        }
    }
    
    /**
     * Schedules a new idleOutTask.
     */
    protected void setIdleOutTask() {
        if (get("idlewarning") < get("idle")) {
            idleWarningTask = new IdleWarningTask(currentPlayer, this);
            gameTimer.schedule(idleWarningTask, get("idlewarning")*1000);
        }
        idleOutTask = new IdleOutTask(currentPlayer, this);
        gameTimer.schedule(idleOutTask, get("idle")*1000);
    }
    
    /**
     * Cancels any scheduled idleOutTask.
     */
    protected void cancelIdleOutTask() {
        if (idleOutTask != null){
            idleWarningTask.cancel();
            idleOutTask.cancel();
            gameTimer.purge();
        }
    }
    
    /* 
     * Player management methods 
     * Controls joining, leaving, waitlisting, and bankruptcies. Also includes
     * toggling of simple, paying debt.
     */
    
    /**
     * Adds a player to the joined list.
     * @param nick the player's nick
     * @param host the player's host
     */
    abstract protected void addPlayer(String nick, String host);
    
    /**
     * Adds a player to the waitlist.
     * @param nick the player's nick
     * @param host the player's host
     */
    abstract protected void addWaitlistPlayer(String nick, String host);
    
    /**
     * Adds a player to the joined list.
     * @param p the player
     */
    protected void addPlayer(Player p){
        User user = findUser(p.getNick());
        joined.add(p);
        loadDBPlayerData(p);
        if (user != null){
            manager.voice(channel, user);
        }
        showMsg(getMsg("join"), p.getNickStr(), joined.size());
    }
    
    /**
     * Checks if the player is on the joined list.
     * @param nick the player's nick
     * @return true if on the joined list
     */
    public boolean isJoined(String nick){
        return (findJoined(nick) != null);
    }
    
    /**
     * Checks if the player is on the waitlist.
     * @param nick the player's nick
     * @return true if on the waitlist
     */
    public boolean isWaitlisted(String nick){
        return (findWaitlisted(nick) != null);
    }
    
    /**
     * Checks if the player is on the blacklist.
     * @param nick the player's nick
     * @return true if on the blacklist
     */
    public boolean isBlacklisted(String nick){
        return (findBlacklisted(nick) != null);
    }
    
    /**
     * Removes the specified player from the joined list, based on nick.
     * @param nick the player's nick
     */
    protected void removeJoined(String nick){
        Player p = findJoined(nick);
        removeJoined(p);
    }
    
    /**
     * Removes the specified player from the joined list.
     * @param p the player
     */
    protected void removeJoined(Player p){
        User user = findUser(p.getNick());
        joined.remove(p);
        saveDBPlayerData(p);
        if (user != null){
            manager.deVoice(channel, user);
        }
    }
    
    /**
     * Removes the specified player from the waitlist based on nick.
     * @param nick the player's nick
     */
    protected void removeWaitlisted(String nick){
        Player p = findWaitlisted(nick);
        removeWaitlisted(p);
    }
    
    /**
     * Removes the specified player from the waitlist.
     * @param p the player
     */
    protected void removeWaitlisted(Player p){
        waitlist.remove(p);
    }

    /**
     * Removes the specified player from the blacklist.
     * @param p the player
     */
    protected void removeBlacklisted(Player p){
        blacklist.remove(p);
    }
    
    /**
     * Searches for a user in the game channel.
     * @param nick the user's nick
     * @return the User instance or null if not found
     */
    protected User findUser(String nick){
        for (User user : manager.getUsers(channel)) {
            if (user.getNick().equalsIgnoreCase(nick)){
                return user;
            }
        }
        return null;
    }
    
    /**
     * Searches for a player who has joined.
     * @param nick the player's nick
     * @return the Player instance or null if not found
     */
    protected Player findJoined(String nick){
        for (Player p : joined) {
            if (p.getNick().equalsIgnoreCase(nick)){
                return p;
            }  
        }
        return null;
    }
    
    /**
     * Searches for a player on the waitlist.
     * @param nick the player's nick
     * @return the Player instance or null if not found
     */
    protected Player findWaitlisted(String nick){
        for (Player p : waitlist) {
            if (p.getNick().equalsIgnoreCase(nick)){
                return p;
            }  
        }
        return null;
    }
    
    /**
     * Searches for a player on the blacklist.
     * @param nick the player's nick
     * @return the Player instance or null if not found
     */
    protected Player findBlacklisted(String nick){
        for (Player p : blacklist) {
            if (p.getNick().equalsIgnoreCase(nick)){
                return p;
            }  
        }
        return null;
    }
    
    /**
     * Returns the player that comes next after currentPlayer.
     * Returns null if currentPlayer is the last player.
     * @return the next player or null
     */
    protected Player getNextPlayer(){
        int index = joined.indexOf(currentPlayer);
        if (index + 1 < joined.size()){
            return joined.get(index + 1);
        } else {
            return null;
        }
    }
    
    /**
     * Resets a player's in-game properties.
     * @param p 
     */
    abstract protected void resetPlayer(Player p);
    
    /**
     * Transfers the specified amount from a player's stack to his bankroll.
     * A negative amount indicates a withdrawal. A positive amount indicates
     * a deposit.
     * 
     * @param nick the player's nick
     * @param amount the amount to transfer
     */
    protected void transfer(String nick, int amount){
        Player p = findJoined(nick);
        // Ignore a transfer of $0
        if (amount == 0){
            informPlayer(nick, getMsg("no_transaction"));
        // Disallow withdrawals for bankrolls with insufficient funds
        } else if (amount < 0 && p.getInteger("bank") < -amount){
            informPlayer(nick, getMsg("no_withdrawal"));
        // Disallow deposits of amounts larger than cash
        } else if (amount > 0 && amount > p.getInteger("cash")){
            informPlayer(nick, getMsg("no_deposit_cash"));
        // Disallow deposits that leave the player with $0 cash
        } else if (amount > 0 && amount == p.getInteger("cash")){
            informPlayer(nick, getMsg("no_deposit_bankrupt"));
        } else {
            p.bankTransfer(amount);
            saveDBPlayerBanking(p);
            if (amount > 0){
                showMsg(getMsg("deposit"), p.getNickStr(), amount, p.get("cash"), p.get("bank"));
            } else {
                showMsg(getMsg("withdraw"), p.getNickStr(), -amount, p.get("cash"), p.get("bank"));
            }
        }
    }

    /**
     * Devoices all players joined in a game and saves their data.
     * This method only needs to be called when a game is shutdown.
     */
    protected void devoiceAll(){
        String modeSet = "";
        String nickStr = "";
        int count = 0;
        for (Player p : joined) {
            saveDBPlayerData(p);
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
    
    /////////////////////////////////////////
    //// Player stats management methods ////
    /////////////////////////////////////////
    
    /**
     * Loads players.txt.
     * Reads the file's contents into an ArrayList of PlayerRecord.
     * 
     * @return 
     */
    protected ArrayList<Player> loadPlayerFile() {
        String nick;
        int cash, bank, bankrupts, bjwinnings, bjrounds, tpwinnings, tprounds, ttwins, ttplayed;
        try (BufferedReader in = new BufferedReader(new FileReader("players.txt"))) {
            ArrayList<Player> records = new ArrayList<>();
            Player record;
            StringTokenizer st;
            
            while (in.ready()){
                st = new StringTokenizer(in.readLine());
                record = new Player("");
                record.put("nick", st.nextToken());
                record.put("cash", Integer.valueOf(st.nextToken()));
                record.put("bank", Integer.valueOf(st.nextToken()));
                record.put("bankrupts", Integer.valueOf(st.nextToken()));
                record.put("bjwinnings", Integer.valueOf(st.nextToken()));
                record.put("bjrounds", Integer.valueOf(st.nextToken()));
                record.put("tpwinnings", Integer.valueOf(st.nextToken()));
                record.put("tprounds", Integer.valueOf(st.nextToken()));
                record.put("ttwins", Integer.valueOf(st.nextToken()));
                record.put("ttplayed", Integer.valueOf(st.nextToken()));
                records.add(record);
            }
            return records;
        } catch (IOException e) {
            manager.log("Error reading players.txt!");
            return null;
        }
    }
    
    /**
     * Saves data to players.txt.
     * Saves an ArrayList of StatFileLine to the file.
     * 
     * @param records
     */
    protected void savePlayerFile(ArrayList<Player> records) {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("players.txt")))) {
            for (Player record : records) {
                out.println(record);
            }
        } catch (IOException e) {
            manager.log("Error writing to players.txt!");
        }
    }
    
    /**
     * Uses the GameManager interface to log DB warnings.
     * @param warning 
     */
    protected void logDBWarning(SQLWarning warning) {
        while (warning != null) {
            manager.log("Message: " + warning.getMessage());
            manager.log("SQLState: " + warning.getSQLState());
            manager.log("Vendor error code: " + warning.getErrorCode());
            warning = warning.getNextWarning();
        }
    }
    
    /**
     * Returns the player record for the specified nick for the game.
     * @param nick
     * @return 
     */
    abstract protected Player loadDBPlayerRecord(String nick);
    
    /**
     * Loads a player's stats from the database.
     * @param p
     */
    abstract protected void loadDBPlayerData(Player p);
    
    /**
     * Saves a list of players in one transaction.
     * @param players 
     */
    abstract protected void saveDBPlayerDataBatch(ArrayList<Player> players);
    
    /**
     * Saves a player's stats to the database.
     * @param p
     */
    protected void saveDBPlayerData(Player p) {
        ArrayList<Player> list = new ArrayList<>(1);
        list.add(p);
        saveDBPlayerDataBatch(list);
    }
    
    /**
     * Records a banking transaction into the database.
     * @param p 
     */
    protected void saveDBPlayerBanking(Player p) {
        try (Connection conn = DriverManager.getConnection(dbURL)) {
            // Insert banking transaction into Banking table
            String sql = "INSERT INTO Banking (player_id, transaction_time, " +
                         "cash_change, cash, bank) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, p.getInteger("id"));
                ps.setLong(2, System.currentTimeMillis() / 1000);
                ps.setInt(3, p.getInteger("transaction"));
                ps.setInt(4, p.getInteger("cash"));
                ps.setInt(5, p.getInteger("bank"));
                ps.executeUpdate();
            }
            
            logDBWarning(conn.getWarnings());
        } catch (SQLException ex) {
            manager.log("SQL Error: " + ex.getMessage());
        }
    }
    
    ////////////////////////////////////////
    //// Game stats management methods. ////
    ////////////////////////////////////////
    
    /**
     * saves game stats to the database.
     */
    abstract protected void saveDBGameStats();
    
    /**
     * Saves a list of hosts to the specified file.
     * @param file the file path
     * @param hostList ArrayList of hosts
     */
    protected final void saveHostList(String file, ArrayList<String> hostList){
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            for (String host : hostList){
                out.println(host);
            }
        } catch (IOException e){
            manager.log("Error writing to " + file + "!");
        }      
    }
    
    /**
     * Loads a list of hosts from the specified file.
     * @param file the file path
     * @param hostList
     */
    protected final void loadHostList(String file, ArrayList<String> hostList){
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            while (in.ready()) {
                hostList.add(in.readLine());
            }
        } catch (IOException e){
            manager.log("Creating " + file + "...");
            saveHostList(file, hostList);
        }      
    }
    
    /////////////////////////////////////////
    //// Generic card management methods ////
    /////////////////////////////////////////
    
    /**
     * Takes a card from the deck and adds it to the discard pile.
     */
    protected void burnCard() {
        deck.addToDiscard(deck.takeCard());
    }
    
    /**
     * Takes a card from the deck and adds it to the specified hand.
     * @param h
     */
    protected void dealCard(Hand h) {
        h.add(deck.takeCard());
    }
    
    ////////////////////////////////
    //// Message output methods ////
    ////////////////////////////////
    
    /**
     * Outputs a player's rank for the given stat to the game channel.
     * 
     * @param nick the player's nick
     * @param stat the stat name
     */
    abstract protected void showPlayerRank(String nick, String stat) throws IllegalArgumentException;
    
    /**
     * Outputs the top N players for a given statistic.
     * Outputs the list of players sorted by ranking to the game channel.
     * 
     * @param stat the statistic used for ranking
     * @param n the length of the list up to n players
     */
    abstract protected void showTopPlayers(String stat, int n) throws IllegalArgumentException;
    
    /**
     * Sends a message to the game channel.
     * @param msg the message to send
     * @param args the parameters for the message
     */
    protected final void showMsg(String msg, Object... args){
        manager.sendMessage(channel, String.format(msg, args));
    }
    
    /**
     * Outputs the start round message to the game channel.
     */
    protected void showStartRound(){
        if (startCount > 0){
            showMsg(getMsg("start_round_auto"), getGameNameStr(), get("startwait"), startCount);
        } else {
            showMsg(getMsg("start_round"), getGameNameStr(), get("startwait"));
        }
    }
    
    /**
     * Outputs the amount in a player's stack to the game channel.
     * @param nick the player's nick
     */
    protected void showPlayerCash(String nick){
        Player record = loadDBPlayerRecord(nick);
        if (record == null) {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        } else {
            showMsg(getMsg("player_cash"), record.getNick(false), record.get("cash"));
        }
    }
    
    /**
     * Outputs a player's net cash to the game channel.
     * @param nick the player's nick
     */
    protected void showPlayerNetCash(String nick){
        Player record = loadDBPlayerRecord(nick);
        if (record == null) {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        } else {
            showMsg(getMsg("player_net"), record.getNick(false), record.get("netcash"));
        }
    }
    
    /**
     * Outputs the amount in a player's bank to the game channel.
     * @param nick the player's nick
     */
    protected void showPlayerBank(String nick){
        Player record = loadDBPlayerRecord(nick);
        if (record == null) {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        } else {
            showMsg(getMsg("player_bank"), record.getNick(false), record.get("bank"));
        }
    }
    
    /**
     * Outputs the number of times a player has gone bankrupt to the game 
     * channel.
     * @param nick the player's nick
     */
    protected void showPlayerBankrupts(String nick){
        Player record = loadDBPlayerRecord(nick);
        if (record == null) {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        } else {
            showMsg(getMsg("player_bankrupts"), record.getNick(false), record.get("bankrupts"));
        }
    }
    
    /**
     * Outputs a player's winnings for the game to the game channel.
     * @param nick the player's nick
     */
    public void showPlayerWinnings(String nick){
        Player record = loadDBPlayerRecord(nick);
        if (record == null) {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        } else {
            showMsg(getMsg("player_winnings"), record.getNick(false), record.get("winnings"), getGameNameStr());
        }
    }
    
    /**
     * Outputs a player's win rate for the game to the game channel.
     * @param nick the player's nick
     */
    public void showPlayerWinRate(String nick){
        Player record = loadDBPlayerRecord(nick);
        if (record == null) {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        } else if (record.getInteger("rounds") == 0){
            showMsg(getMsg("player_no_rounds"), record.getNick(false), getGameNameStr());
        } else {
            showMsg(getMsg("player_winrate"), record.getNick(false), record.get("winrate"), getGameNameStr());
        }
    }
    
    /**
     * Outputs the number of rounds of the game a player has played to
     * the game channel.
     * @param nick the player's nick
     */
    public void showPlayerRounds(String nick){
        Player record = loadDBPlayerRecord(nick);
        if (record == null) {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        } else if (record.getInteger("rounds") == 0) {
            showMsg(getMsg("player_no_rounds"), record.getNick(false), getGameNameStr());
        } else {
            showMsg(getMsg("player_rounds"), record.getNick(false), record.get("rounds"), getGameNameStr());
        }
    }
    
    /**
     * Outputs all of a player's stats relevant to this game.
     * Sends the list of statistics separated by '|' character to the game
     * channel.
     * 
     * @param nick the player's nick
     */
    public void showPlayerAllStats(String nick){
        Player record = loadDBPlayerRecord(nick);
        if (record == null) {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        } else {
            showMsg(getMsg("player_all_stats"), record.getNick(false), record.get("cash"), record.get("bank"), record.get("netcash"), record.get("bankrupts"), record.get("winnings"), record.get("rounds"));
        }
    }
    
    /////////////////////////////////////////////
    //// Private message methods to players. ////
    /////////////////////////////////////////////
    /**
     * Sends a message to a player via PM or notice depending on simple status.
     * @param nick the player's nick
     * @param message the message
     * @param args parameters to be included in the message
     */
    protected void informPlayer(String nick, String message, Object... args){
        User user = findUser(nick);
        // Make sure the user is still in the channel
        if (user != null) {
            String host = user.getHostmask();
            if (notSimpleList.contains(host)) {
                manager.sendMessage(nick, String.format(message, args));
            } else {
                manager.sendNotice(nick, String.format(message, args));
            }
        }
    }
    
    /**
     * Reveals cards from the card deck.
     * @param nick the player
     * @param type undealt cards or discards
     * @param num the number of cards to reveal
     */
    protected void infoDeckCards(String nick, char type, int num) throws IllegalArgumentException{
        if (num < 1){
            throw new IllegalArgumentException();
        }
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
            cardStr += "-" + (cardIndex+numOut-1) + ":";
            n -= numOut;
            for (int ctr=cardIndex; ctr < cardIndex+numOut; ctr++){
                cardStr += " " + tCards.get(ctr);
            }
            cardIndex += numOut;
            informPlayer(nick, cardStr + Colors.NORMAL);
        }
    }
    
    ///////////////////////////
    //// Formatted strings ////
    ///////////////////////////
    
    /**
     * Returns the name of the game formatted in bold.
     * @return the game name in bold
     */
    abstract public String getGameNameStr();
    
    /**
     * Returns the rules for the game.
     * @return the rules
     */
    abstract protected String getGameRulesStr();
    
    /**
     * Returns the stats for the game.
     * @return the stats
     */
    abstract protected String getGameStatsStr();
    
    /**
     * Returns a list of commands for this game based on its help file.
     * @return a list of commands
     */
    protected String getCommandsStr() {
        String commandList = "";
        String[] keys = cmdMap.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        for (String key : keys) {
            commandList += key + ", ";
        }
        return commandList.substring(0, commandList.length() - 2);
    }
    
    /**
     * Returns a list of Op commands for this game based on its help file.
     * @return a list of Op commands
     */
    protected String getOpCommandsStr() {
        String commandList = "";
        String[] keys = opCmdMap.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        for (String key : keys) {
            commandList += key + ", ";
        }
        return commandList.substring(0, commandList.length() - 2);
    }
    
    /**
     * Returns a list of settings for this game based on settings.
     * @return a list of settings
     */
    protected String getSettingsStr() {
        String settingsList = "";
        String[] keys = settings.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        for (String key : keys) {
            settingsList += key + ", ";
        }
        return settingsList.substring(0, settingsList.length() - 2);
    }
    
    public static String formatDecimal(double n) {
        return String.format("%,.2f", n);
    }
    public static String formatNoDecimal(double n) {
        return String.format("%,.0f", n);
    }
    public static String formatNumber(int n){
        return String.format("%,d", n);
    }
    public static String formatHeader(String str){
        return Colors.BOLD + Colors.YELLOW + ",01" + str + Colors.NORMAL;
    }
    public static String formatBold(String str){
        return Colors.BOLD + str + Colors.BOLD;
    }
    public static String formatBold(int value){
        return formatBold(value + "");
    }
    public static String formatNoPing(String str) {
        return str.substring(0,1) + "\u200b" + str.substring(1);
    }
    
    /**
     * Generates a string from a list of players.
     * @param playerList the list of players
     * @return a string containing number of players and the players' nicks
     */
    protected static String getPlayerListString(ArrayList<? extends Player> playerList){
        int size = playerList.size();
        String outStr = formatBold(size);
        if (size == 0){
            outStr += " players";
        } else if (size == 1){
            outStr += " player: ";
        } else {
            outStr += " players: ";
        }
        for (int ctr=0; ctr < size; ctr++){
            if (ctr == size-1){
                outStr += playerList.get(ctr).getNick();
            } else {
                outStr += playerList.get(ctr).getNick()+", ";
            }
        }
        return outStr;
    }
    
    /**
     * Returns the help data on the specified command.
     * @param command
     * @return the help data for the command
     */
    protected String getCommandHelp(String command){
        if (cmdMap.containsKey(command)){
            return cmdMap.get(command);
        } else if (opCmdMap.containsKey(command)) {
            return opCmdMap.get(command);
        } else if (aliasMap.containsKey(command)) {
            if (cmdMap.containsKey(aliasMap.get(command))) {
                return cmdMap.get(aliasMap.get(command));
            } else {
                return opCmdMap.get(aliasMap.get(command));
            }
        } else {
            return "Error: Help for \'" + command + "\' not found!";
        }
    }
    
    /**
     * Returns the message based on the specified key.
     * @param msgKey the message key
     * @return the message
     */
    protected String getMsg(String msgKey) {
        if (msgMap.containsKey(msgKey)){
            return msgMap.get(msgKey);
        } else {
            return "Error: Message for \'" + msgKey + "\' not found!";
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof CardGame) {
            CardGame c = (CardGame) o;
            if (channel.equals(c.channel) && name.equals(c.name) &&
                hashCode() == c.hashCode()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + channel.hashCode();
        hash = 17 * hash + name.hashCode();
        return hash;
    }
}