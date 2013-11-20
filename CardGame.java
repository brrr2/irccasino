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
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.NickChangeEvent;
import org.pircbotx.hooks.events.PartEvent;
import org.pircbotx.hooks.events.QuitEvent;

/**
 * Superclass for card games written for irccasino.
 * @author Yizhe Shen
 */
public abstract class CardGame extends ListenerAdapter<PircBotX> {
    
    protected CasinoBot bot;
    protected Channel channel;
    protected char commandChar;
    protected ArrayList<Player> joined, blacklist, waitlist; //Player lists
    protected CardDeck deck; //the deck of cards
    protected Player currentPlayer; //stores the player whose turn it is
    protected Timer gameTimer; //All TimerTasks are scheduled on this Timer
    protected HashMap<String,Integer> settingsMap;
    protected HashMap<String,String> helpMap, msgMap;
    private String name, iniFile, helpFile, strFile;
    private IdleOutTask idleOutTask;
    private IdleWarningTask idleWarningTask;
    private StartRoundTask startRoundTask;
    private ArrayList<RespawnTask> respawnTasks;
    
    /* Start round task to be performed after post-start waiting period */
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
    /* Idle task for removing idle players */
    public static class IdleOutTask extends TimerTask {
        private Player player;
        private CardGame game;
        public IdleOutTask(Player p, CardGame g) {
            player = p;
            game = g;
        }

        @Override
        public void run() {
            game.showMsg(game.getMsg("idle_out"), player.getNickStr());
            game.leave(player);
        }
    }
    /* Idle warning task for reminding players they are about to idle out */
    public static class IdleWarningTask extends TimerTask {
        private Player player;
        private CardGame game;
        public IdleWarningTask(Player p, CardGame g) {
            player = p;
            game = g;
        }
        
        @Override
        public void run(){
            game.informPlayer(player.getNick(), game.getMsg("idle_warning"),
                    player.getNickStr(), game.get("idle") - game.get("idlewarning"));
        }
    }
    /* Respawn task for giving loans after bankruptcies */
    public static class RespawnTask extends TimerTask {
        Player player;
        CardGame game;
        public RespawnTask(Player p, CardGame g) {
            player = p;
            game = g;
        }
        @Override
        public void run() {
            ArrayList<RespawnTask> tasks = game.getRespawnTasks();
            player.set("cash", game.get("cash"));
            player.add("bank", -game.get("cash"));
            game.showMsg(game.getMsg("respawn"), player.getNickStr(), game.get("cash"));
            game.savePlayerData(player);
            game.removeBlacklisted(player);
            tasks.remove(this);
        }
    }
    
    /**
     * Creates a generic CardGame.
     * Not to be directly instantiated.
     * 
     * @param parent The bot that uses an instance of this class
     * @param commChar The command char
     * @param gameChannel The IRC channel in which the game is to be run.
     */
    public CardGame (CasinoBot parent, char commChar, Channel gameChannel){
        bot = parent;
        commandChar = commChar;
        channel = gameChannel;
        joined = new ArrayList<Player>();
        blacklist = new ArrayList<Player>();
        waitlist = new ArrayList<Player>();
        respawnTasks = new ArrayList<RespawnTask>();
        settingsMap = new HashMap<String,Integer>();
        helpMap = new HashMap<String,String>();
        msgMap = new HashMap<String,String>();
        gameTimer = new Timer("Game Timer");
        startRoundTask = null;
        idleOutTask = null;
        idleWarningTask = null;
        currentPlayer = null;
        checkPlayerFile();
    }
    
    @Override
    public void onMessage(MessageEvent<PircBotX> event){
        String msg = event.getMessage();
        
        // Parse the message if it is a command
        if (msg.length() > 1 && msg.charAt(0) == commandChar && 
                msg.charAt(1) != ' ' && event.getChannel().equals(channel)){
            StringTokenizer st = new StringTokenizer(msg.substring(1));
            String command = st.nextToken().toLowerCase();
            String[] params = new String[st.countTokens()];
            for (int ctr = 0; ctr < params.length; ctr++){
                params[ctr] = st.nextToken();
            }
            
            processCommand(event.getUser(), command, params);
        }
    }
    
    @Override
    public void onJoin(JoinEvent<PircBotX> event){
        if (event.getChannel().equals(channel)){
            processJoin(event.getUser());
        }
    }

    @Override
    public void onPart(PartEvent<PircBotX> event){
        if (event.getChannel().equals(channel)){
            processQuit(event.getUser());
        }
    }

    @Override
    public void onQuit(QuitEvent<PircBotX> event){
        processQuit(event.getUser());
    }

    @Override
    public void onNickChange(NickChangeEvent<PircBotX> event){
        processNickChange(event.getUser(), event.getOldNick(), event.getNewNick());
    }
    
    /* Methods that process IRC events */
    /**
     * Processes commands in the channel where the game is running.
     * 
     * @param user IRC user who issued the command.
     * @param command The command that was issued.
     * @param params A list of parameters that were passed along.
     */
    public void processCommand(User user, String command, String[] params){
        String nick = user.getNick();
        
        /* Common commands for all games that can be called at anytime or have 
         * the same permissions. */
        if (command.equals("leave") || command.equals("quit") || command.equals("l") || command.equals("q")){
            leave(nick);
        } else if (command.equals("cash")) {
            if (params.length > 0){
                showPlayerCash(params[0]);
            } else {
                showPlayerCash(nick);
            }
        } else if (command.equals("netcash") || command.equals("net")) {
            if (params.length > 0){
                showPlayerNetCash(params[0]);
            } else {
                showPlayerNetCash(nick);
            }
        } else if (command.equals("bank")) {
            if (params.length > 0){
                showPlayerBank(params[0]);
            } else {
                showPlayerBank(nick);
            }
        } else if (command.equals("bankrupts")) {
            if (params.length > 0){
                showPlayerBankrupts(params[0]);
            } else {
                showPlayerBankrupts(nick);
            }
        } else if (command.equals("winnings")) {
            if (params.length > 0){
                showPlayerWinnings(params[0]);
            } else {
                showPlayerWinnings(nick);
            }
        } else if (command.equals("winrate")) {
            if (params.length > 0){
                showPlayerWinRate(params[0]);
            } else {
                showPlayerWinRate(nick);
            }
        } else if (command.equals("rounds")) {
            if (params.length > 0){
                showPlayerRounds(params[0]);
            } else {
                showPlayerRounds(nick);
            }
        } else if (command.equals("player") || command.equals("p")){
            if (params.length > 0){
                showPlayerAllStats(params[0]);
            } else {
                showPlayerAllStats(nick);
            }
        } else if (command.equals("transfer") || command.equals("deposit") || 
                    command.equals("withdraw")) {
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else if (has("inprogress")) {
                informPlayer(nick, getMsg("wait_round_end"));
            } else {
                if (params.length > 0){
                    try {
                        if (command.equals("transfer") || command.equals("deposit")) {
                            transfer(nick, Integer.parseInt(params[0]));
                        } else {
                            transfer(nick, -Integer.parseInt(params[0]));
                        }
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equals("waitlist")) {
            showMsg(getMsg("waitlist"), getPlayerListString(waitlist));
        } else if (command.equals("blacklist")) {
            showMsg(getMsg("blacklist"), getPlayerListString(blacklist));
        } else if (command.equals("rank")) {
            if (has("inprogress")) {
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
            if (has("inprogress")) {
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
                        showTopPlayers("cash", Integer.parseInt(params[0]));
                    } catch (IllegalArgumentException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    showTopPlayers("cash", 5);
                }
            }
        } else if (command.equals("simple")) {
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else {
                togglePlayerSimple(nick);
            }
        } else if (command.equals("stats")){
            if (has("inprogress")) {
                informPlayer(nick, getMsg("wait_round_end"));
            } else {
                showGameStats();
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
            informPlayer(nick, getGameCommandStr());
        } else if (command.equals("game")) {
            showMsg(getMsg("game_name"), getGameNameStr());
        // Op Commands
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
        } else if (command.equals("ftransfer") || command.equals("fdeposit") ||
                command.equals("fwithdraw")) {
            if (!channel.isOp(user)) {
                informPlayer(nick, getMsg("ops_only"));
            } else {
                if (params.length > 1) {
                    if (!isJoined(params[0])) {
                        informPlayer(nick, params[0] + " is not currently joined!");
                    } else if (has("inprogress")) {
                        informPlayer(nick, getMsg("wait_round_end"));
                    } else {
                        try {
                            if (command.equals("ftransfer") || command.equals("fdeposit")) {
                                transfer(params[0], Integer.parseInt(params[1]));
                            } else {
                                transfer(params[0], -Integer.parseInt(params[1]));
                            }
                        } catch (NumberFormatException e) {
                            informPlayer(nick, getMsg("bad_parameter"));
                        }
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equals("cards") || command.equals("discards")) {
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        int num = Integer.parseInt(params[0]);
                        if (command.equals("cards") && deck.getNumberCards() > 0) {
                            infoDeckCards(nick, 'c', num);
                        } else if (command.equals("discards") && deck.getNumberDiscards() > 0) {
                            infoDeckCards(nick, 'd', num);
                        } else {
                            informPlayer(nick, "Empty!");
                        }
                    } catch (IllegalArgumentException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equals("set")){
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 1){
                    try {
                        set(params[0].toLowerCase(), Integer.parseInt(params[1]));
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
     * Processes a user join event in the channel where the game is running.
     * 
     * @param user The IRC user who has joined.
     */
    public void processJoin(User user){
        String nick = user.getNick();
        if (loadPlayerStat(nick, "exists") != 1){
            informPlayer(nick, getMsg("new_nick"), getGameNameStr(), commandChar);
        }
    }
    
    /**
     * Processes a user quit or part event in the channel where the game is running.
     * 
     * @param user The IRC user who has quit or parted.
     */
    public void processQuit(User user){
        String nick = user.getNick();
        if (isJoined(nick) || isWaitlisted(nick)){
            leave(nick);
        }
    }
    
    /**
     * Process a user change nick event in the channel where the game is running.
     * 
     * @param user The IRC user who has changed nicks.
     * @param oldNick The old nick of the user.
     * @param newNick The new nick of the user.
     */
    public void processNickChange(User user, String oldNick, String newNick){
        String hostmask = user.getHostmask();
        if (isJoined(oldNick) || isWaitlisted(oldNick)){
            informPlayer(newNick, getMsg("nick_change"));
            if (isJoined(oldNick)){
                bot.deVoice(channel, user);
                leave(oldNick);
            } else if(isWaitlisted(oldNick)){
                removeWaitlisted(oldNick);
            }
            join(newNick, hostmask);
        }
    }
    
    /* 
     * Accessor methods 
     * Returns or sets object properties.
     */
    public String getName() {
        return name;
    }
    public void setName(String str) {
        name = str;
    }
    public Channel getChannel(){
        return channel;
    }
    public void setIniFile(String file){
        iniFile = file;
    }
    public String getIniFile(){
        return iniFile;
    }
    public void setHelpFile(String file) {
        helpFile = file;
    }
    public String getHelpFile(){
        return helpFile;
    }
    public void setStrFile(String file) {
        strFile = file;
    }
    public String getStrFile(){
        return strFile;
    }
    
    /* 
     * Game management methods
     * These methods control the game flow. 
     */
    abstract public void startRound();
    abstract public void continueRound();
    abstract public void endRound();
    abstract public void endGame();
    abstract public void resetGame();
    abstract public void leave(String nick);
    abstract protected void saveIniFile();
    protected void initialize() {
        // In-game properties
        settingsMap.put("inprogress", 0);
        settingsMap.put("endround", 0);
        settingsMap.put("startcount", 0);
    }
    protected void set(String setting, int value) throws IllegalArgumentException {
        if (settingsMap.containsKey(setting)) {
            settingsMap.put(setting, value);
        } else {
            throw new IllegalArgumentException();
        }
        saveIniFile();
    }
    protected int get(String setting) throws IllegalArgumentException {
        if (settingsMap.containsKey(setting)){
            return settingsMap.get(setting);
        } else {
            throw new IllegalArgumentException();
        }
    }
    public boolean has(String setting) throws IllegalArgumentException {
        if (settingsMap.containsKey(setting)){
            return get(setting) > 0;
        } else {
            throw new IllegalArgumentException();
        }
    }
    public void increment(String setting) throws IllegalArgumentException {
        if (settingsMap.containsKey(setting)){
            set(setting, get(setting) + 1);
        } else {
            throw new IllegalArgumentException();
        }
    }
    public void decrement(String setting) throws IllegalArgumentException {
        if (settingsMap.containsKey(setting)){
            set(setting, get(setting) - 1);
        } else {
            throw new IllegalArgumentException();
        }
    }
    
    /**
     * Loads the settings from the game's INI file.
     */
    protected void loadIni() {
        try {
            BufferedReader in = new BufferedReader(new FileReader(getIniFile()));
            String str;
            StringTokenizer st;
            while (in.ready()) {
                str = in.readLine();
                if (!str.startsWith("#")) {
                    st = new StringTokenizer(str, "=");
                    set(st.nextToken(), Integer.parseInt(st.nextToken()));
                }
            }
            in.close();
        } catch (IOException e) {
            /* load defaults if INI file is not found */
            bot.log(getIniFile()+" not found! Creating new " + getIniFile() + "...");
            initialize();
            saveIniFile();
        }
    }
    
    /**
     * Loads String-to-String mapping data from a file.
     * @param stringMap a String-to-String HashMap
     * @param file path to file to load
     */
    protected void loadLib(HashMap<String,String> stringMap, String file) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(file));
            StringTokenizer st;
            String str;
            while (in.ready()){
                // Replace all unicode
                str = in.readLine().replaceAll("u0002", Colors.BOLD);
                // Skips all lines that begin with #
                if (!str.startsWith("#")) {
                    st = new StringTokenizer(str, "=");
                    stringMap.put(st.nextToken(), st.nextToken());
                }
            }
            in.close();
        } catch (IOException e) {
            bot.log("Error reading from " + file + "!");
        }
    }
    
    /**
     * Attempts to add a player to the game.
     * @param nick the player's nick
     * @param host the player's host
     */
    public void join(String nick, String host) {
        CardGame game = bot.getGame(nick);
        if (joined.size() == get("maxplayers")){
            informPlayer(nick, getMsg("max_players"));
        } else if (isJoined(nick)) {
            informPlayer(nick, getMsg("is_joined"));
        } else if (isBlacklisted(nick) || bot.isBlacklisted(nick)) {
            informPlayer(nick, getMsg("on_blacklist"));
        } else if (game != null) {
            informPlayer(nick, getMsg("is_joined_other"), game.getGameNameStr(), game.getChannel().getName());
        } else if (has("inprogress")) {
            if (isWaitlisted(nick)) {
                informPlayer(nick, getMsg("on_waitlist"));
            } else {
                addWaitlistPlayer(nick, host);
            }
        } else {
            addPlayer(nick, host);
        }
    }
    
    /**
     * Attempts to force add a player to the game.
     * @param OpNick the channel Op
     * @param nick the player to be force joined
     * @param host the player's host
     */
    public void fjoin(String OpNick, String nick, String host) {
        CardGame game = bot.getGame(nick);
        if (joined.size() == get("maxplayers")){
            informPlayer(OpNick, getMsg("max_players"));
        } else if (isJoined(nick)) {
            informPlayer(OpNick, getMsg("is_joined_nick"), nick);
        } else if (isBlacklisted(nick) || bot.isBlacklisted(nick)) {
            informPlayer(OpNick, getMsg("on_blacklist_nick"), nick);
        } else if (game != null) {
            informPlayer(OpNick, getMsg("is_joined_other_nick"), nick, game.getGameNameStr(), game.getChannel().getName());
        } else if (has("inprogress")) {
            if (isWaitlisted(nick)) {
                informPlayer(OpNick, getMsg("on_waitlist_nick"), nick);
            } else {
                addWaitlistPlayer(nick, host);
            }
        } else {
            addPlayer(nick, host);
        }
    }
    
    /**
     * Attempts to remove a player from the game.
     * @param p the player
     */
    public void leave(Player p){
        leave(p.getNick());
    }
    
    /**
     * Moves players on the waitlist to the joined list and clears the waitlist.
     */
    public void mergeWaitlist(){
        for (int ctr = 0; ctr < waitlist.size(); ctr++){
            addPlayer(waitlist.get(ctr));
        }
        waitlist.clear();
    }
    
    /**
     * Sets a RespawnTask for a player who has gone bankrupt.
     * @param p the bankrupt player
     */
    public void setRespawnTask(Player p) {
        // Calculate extra time penalty for players with debt
        int penalty = Math.max(-1 * p.get("bank") / 1000 * 60, 0);
        informPlayer(p.getNick(), getMsg("bankrupt_info"), (get("respawn") + penalty)/60.);
        RespawnTask task = new RespawnTask(p, this);
        gameTimer.schedule(task, (get("respawn")+penalty)*1000);
        respawnTasks.add(task);
    }
    
    /**
     * Cancels all RespawnTasks.
     */
    public void cancelRespawnTasks() {
        Player p;
        for (int ctr = 0; ctr < respawnTasks.size(); ctr++) {
            respawnTasks.get(ctr).cancel();
        }
        respawnTasks.clear();
        gameTimer.purge();
        // Fast-track loans
        for (int ctr = 0; ctr < blacklist.size(); ctr++){
            p = blacklist.get(ctr);
            p.set("cash", get("cash"));
            p.add("bank", -get("cash"));
            savePlayerData(p);
        }
    }
    public ArrayList<RespawnTask> getRespawnTasks() {
        return respawnTasks;
    }
    public void setStartRoundTask(){
        startRoundTask = new StartRoundTask(this);
        gameTimer.schedule(startRoundTask, 5000);
    }
    public void cancelStartRoundTask(){
        if (startRoundTask != null){
            startRoundTask.cancel();
            gameTimer.purge();
        }
    }
    public void setIdleOutTask() {
        if (get("idlewarning") < get("idle")) {
            idleWarningTask = new IdleWarningTask(currentPlayer, this);
            gameTimer.schedule(idleWarningTask, get("idlewarning")*1000);
        }
        idleOutTask = new IdleOutTask(currentPlayer, this);
        gameTimer.schedule(idleOutTask, get("idle")*1000);
    }
    public void cancelIdleOutTask() {
        if (idleOutTask != null){
            idleWarningTask.cancel();
            idleOutTask.cancel();
            gameTimer.purge();
        }
    }
    public boolean isOpCommandAllowed(User user, String nick){
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (has("inprogress")) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else {
            return true;
        }
        return false;
    }
    
    /* 
     * Player management methods 
     * Controls joining, leaving, waitlisting, and bankruptcies. Also includes
     * toggling of simple, paying debt.
     */
    abstract public void addPlayer(String nick, String hostmask);
    abstract public void addWaitlistPlayer(String nick, String hostmask);
    public void addPlayer(Player p){
        User user = findUser(p.getNick());
        joined.add(p);
        loadPlayerData(p);
        if (user != null){
            bot.voice(channel, user);
        }
        showMsg(getMsg("join"), p.getNickStr(), joined.size());
    }
    public boolean isJoined(String nick){
        return (findJoined(nick) != null);
    }
    public boolean isJoined(Player p){
        return joined.contains(p);
    }
    public boolean isWaitlisted(String nick){
        return (findWaitlisted(nick) != null);
    }
    public boolean isWaitlisted(Player p){
        return waitlist.contains(p);
    }
    public boolean isBlacklisted(String nick){
        return (findBlacklisted(nick) != null);
    }
    public boolean isBlacklisted(Player p){
        return blacklist.contains(p);
    }
    public void removeJoined(String nick){
        Player p = findJoined(nick);
        removeJoined(p);
    }
    public void removeJoined(Player p){
        User user = findUser(p.getNick());
        joined.remove(p);
        savePlayerData(p);
        if (user != null){
            bot.deVoice(channel, user);
        }
        if (!p.has("cash")) {
            showMsg(getMsg("unjoin_bankrupt"), p.getNickStr(), joined.size());
        } else {
            showMsg(getMsg("unjoin"), p.getNickStr(), joined.size());
        }
    }
    public void removeWaitlisted(String nick){
        Player p = findWaitlisted(nick);
        removeWaitlisted(p);
    }
    public void removeWaitlisted(Player p){
        waitlist.remove(p);
    }
    public void removeBlacklisted(String nick){
        Player p = findBlacklisted(nick);
        removeBlacklisted(p);
    }
    public void removeBlacklisted(Player p){
        blacklist.remove(p);
    }
    public User findUser(String nick){
        User user;
        Iterator<User> it = bot.getUsers(channel).iterator();
        while(it.hasNext()){
            user = it.next();
            if (user.getNick().equalsIgnoreCase(nick)){
                return user;
            }
        }
        return null;
    }
    public Player findJoined(String nick){
        for (int ctr=0; ctr< joined.size(); ctr++){
            Player p = joined.get(ctr);
            if (p.getNick().equalsIgnoreCase(nick)){
                return p;
            }  
        }
        return null;
    }
    public Player findWaitlisted(String nick){
        for (int ctr=0; ctr< waitlist.size(); ctr++){
            Player p = waitlist.get(ctr);
            if (p.getNick().equalsIgnoreCase(nick)){
                return p;
            }  
        }
        return null;
    }
    public Player findBlacklisted(String nick){
        for (int ctr=0; ctr< blacklist.size(); ctr++){
            Player p = blacklist.get(ctr);
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
    public Player getNextPlayer(){
        int index = joined.indexOf(currentPlayer);
        if (index + 1 < joined.size()){
            return joined.get(index + 1);
        } else {
            return null;
        }
    }
    
    /**
     * Toggles a player's "simple" status.
     * Players with "simple" set to true will have game information sent via
     * notice. Players with "simple" set to false will have game information sent
     * via message.
     * 
     * @param nick the player's nick
     */
    public void togglePlayerSimple(String nick){
        Player p = findJoined(nick);
        p.set("simple", (p.get("simple") + 1) % 2);
        if (p.isSimple()){
            bot.sendNotice(nick, "Game info will now be noticed to you.");
        } else {
            bot.sendMessage(nick, "Game info will now be messaged to you.");
        }
    }
    
    /**
     * Transfers the specified amount from a player's stack to his bankroll.
     * A negative amount indicates a withdrawal. A positive amount indicates
     * a deposit.
     * 
     * @param nick the player's nick
     * @param amount the amount to transfer
     */
    public void transfer(String nick, int amount){
        Player p = findJoined(nick);
        // Ignore a transfer of $0
        if (amount == 0){
            informPlayer(nick, getMsg("no_transaction"));
        // Disallow withdrawals for bankrolls with insufficient funds
        } else if (amount < 0 && p.get("bank") < -amount){
            informPlayer(nick, getMsg("no_withdrawal"));
        // Disallow deposits of amounts larger than cash
        } else if (amount > 0 && amount > p.get("cash")){
            informPlayer(nick, getMsg("no_deposit_cash"));
        // Disallow deposits that leave the player with $0 cash
        } else if (amount > 0 && amount == p.get("cash")){
            informPlayer(nick, getMsg("no_deposit_bankrupt"));
        } else {
            p.bankTransfer(amount);
            savePlayerData(p);
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
    public void devoiceAll(){
        Player p;
        String modeSet = "";
        String nickStr = "";
        int count = 0;
        for (int ctr = 0; ctr < joined.size(); ctr++){
            p = joined.get(ctr);
            savePlayerData(p);
            modeSet += "v";
            nickStr += " " + p.getNick();
            count++;
            if (count % 4 == 0) {
                bot.sendRawLine ("MODE " + channel.getName() + " -" + modeSet + nickStr);
                nickStr = "";
                modeSet = "";
                count = 0;
            }
        }
        if (count > 0) {
            bot.sendRawLine ("MODE " + channel.getName() + " -" + modeSet + nickStr);
        }
    }
    
    /* Player file management methods
     * Methods for reading and saving player data.
     */
    
    /**
     * Returns the total number of players who have played this game.
     * Counts the number of players who have played more than one round of this
     * game.
     * 
     * @return the total counted from players.txt
     */
    abstract public int getTotalPlayers();
    
    /**
     * Returns the specified statistic for a nick.
     * If a player is already joined or blacklisted, the value is read from the
     * Player object. Otherwise, a search is done in players.txt.
     * 
     * @param nick IRC user's nick
     * @param stat the statistic's name
     * @return the desired statistic
     */
    protected int getPlayerStat(String nick, String stat){
        if (isJoined(nick) || isBlacklisted(nick)){
            Player p = findJoined(nick);
            if (p == null){
                p = findBlacklisted(nick);
            }
            return p.get(stat);
        }
        return loadPlayerStat(nick, stat);
    }
    
    /**
     * Searches players.txt for a specific player's statistic.
     * If the player or statistic is not found, Integer.MIN_VALUE is returned.
     * 
     * @param nick IRC user's nick
     * @param stat the statistic's name
     * @return the desired statistic or Integer.MIN_VALUE if not found
     */
    protected int loadPlayerStat(String nick, String stat){
        try {
            ArrayList<StatFileLine> statList = new ArrayList<StatFileLine>();
            StatFileLine statLine;
            loadPlayerFile(statList);
            int numLines = statList.size();
            for (int ctr = 0; ctr < numLines; ctr++){
                statLine = statList.get(ctr);
                if (nick.equalsIgnoreCase(statLine.getNick())){
                    return statLine.get(stat);
                }
            }
            return Integer.MIN_VALUE;
        } catch (IOException e){
            bot.log("Error reading players.txt!");
            return Integer.MIN_VALUE;
        }
    }
    
    /**
     * Loads a Player's data from players.txt.
     * If a matching nick is found then all statistics are loaded. Otherwise,
     * the player is loaded with default values.
     * 
     * @param p the Player to find
     */
    protected void loadPlayerData(Player p) {
        try {
            boolean found = false;
            ArrayList<StatFileLine> statList = new ArrayList<StatFileLine>();
            StatFileLine statLine;
            loadPlayerFile(statList);
            int numLines = statList.size();

            for (int ctr = 0; ctr < numLines; ctr++) {
                statLine = statList.get(ctr);
                if (p.getNick().equalsIgnoreCase(statLine.getNick())) {
                    if (statLine.get("cash") <= 0) {
                        p.set("cash", get("cash"));
                    } else {
                        p.set("cash", statLine.get("cash"));
                    }
                    p.set("bank", statLine.get("bank"));
                    p.set("bankrupts", statLine.get("bankrupts"));
                    p.set("bjwinnings", statLine.get("bjwinnings"));
                    p.set("bjrounds", statLine.get("bjrounds"));
                    p.set("tpwinnings", statLine.get("tpwinnings"));
                    p.set("tprounds", statLine.get("tprounds"));
                    p.set("simple", statLine.get("simple"));
                    found = true;
                    break;
                }
            }
            if (!found) {
                p.set("cash", get("cash"));
                informPlayer(p.getNick(), getMsg("new_player"), getGameNameStr(), get("cash"));
            }
        } catch (IOException e) {
            bot.log("Error reading players.txt!");
        }
    }
    
    /**
     * Saves a Player's data into players.txt.
     * If a matching nick is found, the existing data is overwritten. Otherwise,
     * a new line is added for the Player.
     * 
     * @param p the Player to save
     */
    protected void savePlayerData(Player p){
        boolean found = false;
        ArrayList<StatFileLine> statList = new ArrayList<StatFileLine>();
        StatFileLine statLine;
        int numLines;
        
        try {
            loadPlayerFile(statList);
            numLines = statList.size();
            for (int ctr = 0; ctr < numLines; ctr++) {
                statLine = statList.get(ctr);
                if (p.getNick().equalsIgnoreCase(statLine.getNick())) {
                    statLine.set("cash", p.get("cash"));
                    statLine.set("bank", p.get("bank"));
                    statLine.set("bankrupts", p.get("bankrupts"));
                    statLine.set("bjwinnings", p.get("bjwinnings"));
                    statLine.set("bjrounds", p.get("bjrounds"));
                    statLine.set("tpwinnings", p.get("tpwinnings"));
                    statLine.set("tprounds", p.get("tprounds"));
                    statLine.set("simple", p.get("simple"));
                    found = true;
                    break;
                }
            }
            if (!found) {
                statLine = new StatFileLine(p.getNick(), p.get("cash"),
                                        p.get("bank"), p.get("bankrupts"),
                                        p.get("bjwinnings"), p.get("bjrounds"),
                                        p.get("tpwinnings"), p.get("tprounds"),
                                        p.get("simple"));
                statList.add(statLine);
            }
        } catch (IOException e) {
            bot.log("Error reading players.txt!");
        }

        try {
            savePlayerFile(statList);
        } catch (IOException e) {
            bot.log("Error writing to players.txt!");
        }
    }
    
    /**
     * Checks if players.txt exists and creates it if it doesn't exist.
     */
    protected final void checkPlayerFile(){
        try {
            BufferedReader out = new BufferedReader(new FileReader("players.txt"));
            out.close();
        } catch (IOException e){
            bot.log("players.txt not found! Creating new players.txt...");
            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("players.txt")));
                out.close();
            } catch (IOException f){
                bot.log("Error creating players.txt.");
            }
        }
    }
    
    /**
     * Loads players.txt.
     * Reads the file's contents into an ArrayList of StatFileLine.
     * 
     * @param statList 
     * @throws IOException
     */
    public static void loadPlayerFile(ArrayList<StatFileLine> statList) throws IOException {
        String nick;
        int cash, bank, bankrupts, bjwinnings, bjrounds, tpwinnings, tprounds, simple;
        
        BufferedReader in = new BufferedReader(new FileReader("players.txt"));
        StringTokenizer st;
        while (in.ready()){
            st = new StringTokenizer(in.readLine());
            nick = st.nextToken();
            cash = Integer.parseInt(st.nextToken());
            bank = Integer.parseInt(st.nextToken());
            bankrupts = Integer.parseInt(st.nextToken());
            bjwinnings = Integer.parseInt(st.nextToken());
            bjrounds = Integer.parseInt(st.nextToken());
            tpwinnings = Integer.parseInt(st.nextToken());
            tprounds = Integer.parseInt(st.nextToken());
            simple = Integer.parseInt(st.nextToken());
            statList.add(new StatFileLine(nick, cash, bank, bankrupts, bjwinnings, 
                                        bjrounds, tpwinnings, tprounds, simple));
        }
        in.close();
    }
    
    /**
     * Saves data to players.txt.
     * Saves an ArrayList of StatFileLine to the file.
     * 
     * @param statList
     * @throws IOException
     */
    public static void savePlayerFile(ArrayList<StatFileLine> statList) throws IOException {
        int numLines = statList.size();
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("players.txt")));
        
        for (int ctr = 0; ctr<numLines; ctr++){
            out.println(statList.get(ctr));
        }
        out.close();
    }
    
    /* Generic card management methods */
    /**
     * Takes a card from the deck and adds it to the discard pile.
     */
    public void burnCard() {
        deck.addToDiscard(deck.takeCard());
    }
    
    /**
     * Takes a card from the deck and adds it to the specified hand.
     * @param h
     */
    public void dealCard(Hand h) {
        h.add(deck.takeCard());
    }
    
    /* 
     * Channel output methods to reduce clutter.
     * These methods will all send a specific message or set of
     * messages to the main channel.
     */
    abstract public void showGameStats();
    abstract public void showPlayerAllStats(String nick);
    abstract public void showPlayerRank(String nick, String stat);
    abstract public void showTopPlayers(String stat, int n);
    abstract public void showPlayerWinnings(String nick);
    abstract public void showPlayerWinRate(String nick);
    abstract public void showPlayerRounds(String nick); 
    
    /**
     * Sends a message to the game channel.
     * @param msg the message to send
     * @param args the parameters for the message
     */
    public void showMsg(String msg, Object... args){
        bot.sendMessage(channel, String.format(msg, args));
    }
    
    /**
     * Displays the start round message.
     */
    public void showStartRound(){
        if (get("startcount") > 0){
            showMsg(getMsg("start_round_auto"), getGameNameStr(), get("startcount"));
        } else {
            showMsg(getMsg("start_round"), getGameNameStr());
        }
    }
    
    /**
     * Displays a player's cash.
     * @param nick the player
     */
    public void showPlayerCash(String nick){
        int cash = getPlayerStat(nick, "cash");
        if (cash != Integer.MIN_VALUE){
            showMsg(getMsg("player_cash"), formatNoPing(nick), cash);
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }
    
    /**
     * Displays a player's net cash.
     * @param nick the player
     */
    public void showPlayerNetCash(String nick){
        int netcash = getPlayerStat(nick, "netcash");
        if (netcash != Integer.MIN_VALUE){
            showMsg(getMsg("player_net"), formatNoPing(nick), netcash);
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }
    
    /**
     * Displays a player's bank.
     * @param nick the player
     */
    public void showPlayerBank(String nick){
        int bank = getPlayerStat(nick, "bank");
        if (bank != Integer.MIN_VALUE){
            showMsg(getMsg("player_bank"), formatNoPing(nick), bank);
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }
    
    /**
     * Displays a player's number of bankrupts
     * @param nick the player
     */
    public void showPlayerBankrupts(String nick){
        int bankrupts = getPlayerStat(nick, "bankrupts");
        if (bankrupts != Integer.MIN_VALUE){
            showMsg(getMsg("player_bankrupts"), formatNoPing(nick), bankrupts);
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }
    
    /* 
     * Private messages to players.
     * These methods will all send notices/messages to the specified nick.
     */
    /**
     * Sends a message to a player via PM or notice depending on simple status.
     * @param nick
     * @param message
     * @param args 
     */
    public void informPlayer(String nick, String message, Object... args){
        if (getPlayerStat(nick, "simple") > 0) {
            bot.sendNotice(nick, String.format(message, args));
        } else {
            bot.sendMessage(nick, String.format(message, args));
        }
    }
    
    /**
     * Reveals cards from the card deck.
     * @param nick the player
     * @param type undealt cards or discards
     * @param num the number of cards to reveal
     */
    public void infoDeckCards(String nick, char type, int num){
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
    
    /* Formatted strings */
    abstract public String getGameNameStr();
    abstract public String getGameRulesStr();
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
     * Returns a list of commands for this game based on its help file
     * @return a list of commands
     */
    public String getGameCommandStr() {
        StringBuilder commandList = new StringBuilder();
        Set<String> commandSet = helpMap.keySet();
        Iterator<String> it = commandSet.iterator();
        while (it.hasNext()) {
            commandList.append(it.next());
            commandList.append(", ");
        }
        return commandList.substring(0, commandList.length() - 2);
    }
    
    /**
     * Returns the help data on the specified command.
     * @param command
     * @return the help data for the command
     */
    protected String getCommandHelp(String command){
        if (helpMap.containsKey(command)){
            return helpMap.get(command);
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
    
    /**
     * Comparison of CardGame objects based on name and channel.
     * @param o the Object to compare
     * @return true if the properties are the same.
     */
    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof CardGame) {
            CardGame c = (CardGame) o;
            if (channel.equals(c.channel) && getName().equals(c.getName()) &&
                hashCode() == c.hashCode()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + Objects.hashCode(this.channel);
        hash = 17 * hash + Objects.hashCode(this.name);
        return hash;
    }
}