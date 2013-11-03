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

public abstract class CardGame extends ListenerAdapter<PircBotX> {
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
            if (game.isInProgress() && player == game.getCurrentPlayer()) {
                game.bot.sendMessage(game.channel, player.getNickStr()
                                + " has wasted precious time and idled out.");
                game.leave(player);
            }
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
            if (game.isInProgress() && player == game.getCurrentPlayer()) {
                game.infoPlayerIdleWarning(player);
            }
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
            player.set("cash", game.getSetting("cash"));
            player.add("bank", -game.getSetting("cash"));
            game.bot.sendMessage(game.channel, player.getNickStr() + " has been loaned $"
                                + formatNumber(game.getSetting("cash")) + ". Please bet responsibly.");
            game.savePlayerData(player);
            game.removeBlacklisted(player);
            tasks.remove(this);
        }
    }

    protected CasinoBot bot;
    protected Channel channel;
    protected char commandChar;
    protected ArrayList<Player> joined, blacklist, waitlist; //Player lists
    protected CardDeck deck; //the deck of cards
    protected Player currentPlayer; //stores the player whose turn it is
    protected boolean inProgress, betting;
    protected Timer gameTimer; //All TimerTasks are scheduled on this Timer
    protected HashMap<String,Integer> settingsMap;
    private boolean endRound;
    private String gameName, iniFile, helpFile;
    private int startCount;
    private IdleOutTask idleOutTask;
    private IdleWarningTask idleWarningTask;
    private StartRoundTask startRoundTask;
    private ArrayList<RespawnTask> respawnTasks;
    
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
        gameTimer = new Timer("Game Timer");
        startRoundTask = null;
        idleOutTask = null;
        idleWarningTask = null;
        inProgress = false;
        endRound = false;
        betting = false;
        currentPlayer = null;
        startCount = 0;
        checkPlayerFile();
    }
    
    @Override
    public void onMessage(MessageEvent<PircBotX> event){
        String msg = event.getMessage();
        
        // Parse the message if it is a command
        if (msg.length() > 1 && msg.charAt(0) == commandChar && 
                msg.charAt(1) != ' ' && event.getChannel().equals(channel)){
            msg = msg.substring(1);
            StringTokenizer st = new StringTokenizer(msg);
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
        } else if (command.equals("withdraw")){
            if (!isJoined(nick)) {
                infoNotJoined(nick);
            } else if (isInProgress()) {
                infoWaitRoundEnd(nick);
            } else {
                if (params.length > 0){
                    try {
                        transfer(nick, -Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("transfer") || command.equals("deposit")) {
            if (!isJoined(nick)) {
                infoNotJoined(nick);
            } else if (isInProgress()) {
                infoWaitRoundEnd(nick);
            } else {
                if (params.length > 0){
                    try {
                        transfer(nick, Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("waitlist")) {
            showWaitlist();
        } else if (command.equals("blacklist")) {
            showBlacklist();
        } else if (command.equals("rank")) {
            if (isInProgress()) {
                infoWaitRoundEnd(nick);
            } else {
                if (params.length > 1){
                    try {
                        showPlayerRank(params[1].toLowerCase(), params[0].toLowerCase());
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else if (params.length == 1){
                    try {
                        showPlayerRank(nick, params[0].toLowerCase());
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    showPlayerRank(nick, "cash");
                }
            }
        } else if (command.equals("top")) {
            if (isInProgress()) {
                infoWaitRoundEnd(nick);
            } else {
                if (params.length > 1){
                    try {
                        showTopPlayers(params[1].toLowerCase(), Integer.parseInt(params[0]));
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else if (params.length == 1){
                    try {
                        showTopPlayers("cash", Integer.parseInt(params[0]));
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    showTopPlayers("cash", 5);
                }
            }
        } else if (command.equals("simple")) {
            if (!isJoined(nick)) {
                infoNotJoined(nick);
            } else {
                togglePlayerSimple(nick);
            }
        } else if (command.equals("stats")){
            if (isInProgress()) {
                infoWaitRoundEnd(nick);
            } else {
                showGameStats();
            }
        } else if (command.equals("grules")) {
            infoGameRules(nick);
        } else if (command.equals("ghelp")) {
            if (params.length == 0){
                infoGameHelp(nick);
            } else {
                infoGameCommandHelp(nick, params[0].toLowerCase());
            }
        } else if (command.equals("gcommands")) {
            infoGameCommands(nick);
        } else if (command.equals("game")) {
            showGameName();
        // Op Commands
        } else if (command.equals("fl") || command.equals("fq") || command.equals("fquit") || command.equals("fleave")){
            if (!channel.isOp(user)) {
                infoOpsOnly(nick);
            } else {
                if (params.length > 0){
                    leave(params[0]);
                } else {
                    infoNoParameter(nick);
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
                            bot.sendNotice(nick, "Empty!");
                        }
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("set")){
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 1){
                    try {
                        setSetting(params[0].toLowerCase(), Integer.parseInt(params[1]));
                        showUpdateSetting(params[0].toLowerCase());
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("get")) {
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        showSetting(params[0].toLowerCase(), getSetting(params[0]));
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
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
            infoNewNick(nick);
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
            infoNickChange(newNick);
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
    public Channel getChannel(){
        return channel;
    }
    public void setGameName(String name){
        gameName = name;
    }
    public String getGameName(){
        return gameName;
    }
    public void setIniFile(String file){
        iniFile = file;
    }
    public String getIniFile(){
        return iniFile;
    }
    public void setHelpFile(String file){
        helpFile = file;
    }
    public String getHelpFile(){
        return helpFile;
    }
    public void setEndRound(boolean value){
        endRound = value;
    }
    public boolean isEndRound(){
        return endRound;
    }
    public void setStartCount(int value){
        startCount = value;
    }
    public void decStartCount(){
        startCount--;
    }
    public int getStartCount(){
        return startCount;
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
    abstract protected void saveSettings();
    abstract protected void initSettings();
    protected void setSetting(String setting, int value) throws IllegalArgumentException {
        if (settingsMap.containsKey(setting)) {
            settingsMap.put(setting, value);
        } else {
            throw new IllegalArgumentException();
        }
        saveSettings();
    }
    protected int getSetting(String setting) throws IllegalArgumentException {
        if (settingsMap.containsKey(setting)){
            return settingsMap.get(setting);
        } else {
            throw new IllegalArgumentException();
        }
    }
    protected void loadSettings() {
        try {
            BufferedReader in = new BufferedReader(new FileReader(getIniFile()));
            String str, name;
            int value;
            StringTokenizer st;
            while (in.ready()) {
                str = in.readLine();
                if (str.startsWith("#")) {
                    continue;
                }
                st = new StringTokenizer(str, "=");
                name = st.nextToken();
                value = Integer.parseInt(st.nextToken());
                setSetting(name, value);
            }
            in.close();
        } catch (IOException e) {
            /* load defaults if texaspoker.ini is not found */
            bot.log(getIniFile()+" not found! Creating new "+getIniFile()+"...");
            initSettings();
            saveSettings();
        }
    }
    public void join(String nick, String hostmask) {
        if (getNumberJoined() == getSetting("maxplayers")){
            infoMaxPlayers(nick);
        } else if (isJoined(nick)) {
            infoAlreadyJoined(nick);
        } else if (isBlacklisted(nick)) {
            infoBlacklisted(nick);
        } else if (isInProgress()) {
            if (isWaitlisted(nick)) {
                infoAlreadyWaitlisted(nick);
            } else {
                addWaitlistPlayer(nick, hostmask);
            }
        } else {
            addPlayer(nick, hostmask);
        }
    }
    public void leave(Player p){
        leave(p.getNick());
    }
    public void mergeWaitlist(){
        for (int ctr = 0; ctr < waitlist.size(); ctr++){
            addPlayer(getWaitlisted(ctr));
        }
        waitlist.clear();
    }
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
    public void setRespawnTask(Player p) {
        // Calculate extra time penalty for players with debt
        int penalty = Math.max(-1 * p.get("bank") / 1000 * 60, 0);
        infoPlayerBankrupt(p.getNick(), penalty);
        RespawnTask task = new RespawnTask(p, this);
        gameTimer.schedule(task, (getSetting("respawn")+penalty)*1000);
        respawnTasks.add(task);
    }
    public void cancelRespawnTasks() {
        Player p;
        for (int ctr = 0; ctr < respawnTasks.size(); ctr++) {
            respawnTasks.get(ctr).cancel();
        }
        respawnTasks.clear();
        gameTimer.purge();
        // Fast-track loans
        for (int ctr = 0; ctr < getNumberBlacklisted(); ctr++){
            p = getBlacklisted(ctr);
            p.set("cash", getSetting("cash"));
            p.add("bank", -getSetting("cash"));
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
        if (getSetting("idlewarning") < getSetting("idle")) {
            idleWarningTask = new IdleWarningTask(currentPlayer, this);
            gameTimer.schedule(idleWarningTask, getSetting("idlewarning")*1000);
        }
        idleOutTask = new IdleOutTask(currentPlayer, this);
        gameTimer.schedule(idleOutTask, getSetting("idle")*1000);
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
            infoOpsOnly(nick);
        } else if (isInProgress()) {
            infoWaitRoundEnd(nick);
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
        showJoin(p);
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
        showLeave(p);
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
        Set<User> users = bot.getUsers(channel);
        User user;
        Iterator<User> it = users.iterator();
        while(it.hasNext()){
            user = it.next();
            if (user.getNick().equalsIgnoreCase(nick)){
                return user;
            }
        }
        return null;
    }
    public Player findJoined(String nick){
        for (int ctr=0; ctr< getNumberJoined(); ctr++){
            Player p = getJoined(ctr);
            if (p.getNick().equalsIgnoreCase(nick)){
                return p;
            }  
        }
        return null;
    }
    public Player findWaitlisted(String nick){
        for (int ctr=0; ctr< getNumberWaitlisted(); ctr++){
            Player p = getWaitlisted(ctr);
            if (p.getNick().equalsIgnoreCase(nick)){
                return p;
            }  
        }
        return null;
    }
    public Player findBlacklisted(String nick){
        for (int ctr=0; ctr< getNumberBlacklisted(); ctr++){
            Player p = getBlacklisted(ctr);
            if (p.getNick().equalsIgnoreCase(nick)){
                return p;
            }  
        }
        return null;
    }
    public Player getJoined(int num){
        return joined.get(num);
    }
    public Player getWaitlisted(int num){
        return waitlist.get(num);
    }
    public Player getBlacklisted(int num){
        return blacklist.get(num);
    }
    public int getNumberJoined(){
        return joined.size();
    }
    public int getNumberWaitlisted(){
        return waitlist.size();
    }
    public int getNumberBlacklisted(){
        return blacklist.size();
    }
    public Player getCurrentPlayer(){
        return currentPlayer;
    }
    public int getJoinedIndex(Player p){
        return joined.indexOf(p);
    }
    public Player getNextPlayer(){
        int index = joined.indexOf(currentPlayer);
        if (index + 1 < getNumberJoined()){
            return joined.get(index+1);
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
        p.setSimple(!p.isSimple());
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
            infoNoTransaction(nick);
        // Disallow withdrawals for bankrolls with insufficient funds
        } else if (amount < 0 && p.get("bank") < -amount){
            infoNoWithdrawal(nick);
        } else if (amount > 0 && amount > p.get("cash")){
            infoNoDepositCash(nick);
        // Disallow deposits that leave the player with $0 cash
        } else if (amount > 0 && amount == p.get("cash")){
            infoNoDepositBankrupt(nick);
        } else {
            p.bankTransfer(amount);
            savePlayerData(p);
            if (amount > 0){
                showPlayerDeposit(p, amount);
            } else {
                showPlayerWithdraw(p, -amount);
            }
        }
    }
    /**
     * Devoices all players joined in a game and saves their data.
     * This method only needs to be called when a game is shutdown.
     */
    public void devoiceAll(){
        Player p;
        for (int ctr=0; ctr<getNumberJoined(); ctr++){
            p = getJoined(ctr);
            savePlayerData(p);
            bot.deVoice(channel, findUser(p.getNick()));
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
    public int getTotalPlayers(){
        try {
            ArrayList<StatFileLine> statList = new ArrayList<StatFileLine>();
            loadPlayerFile(statList);
            int total = 0, numLines = statList.size();
            
            if (gameName.equals("Blackjack")){
                for (int ctr = 0; ctr < numLines; ctr++){
                    if (statList.get(ctr).get("bjrounds") > 0){
                        total++;
                    }
                }
            } else if (gameName.equals("Texas Hold'em Poker")){
                for (int ctr = 0; ctr < numLines; ctr++){
                    if (statList.get(ctr).get("tprounds") > 0){
                        total++;
                    }
                }
            }
            return total;
        } catch (IOException e){
            bot.log("Error reading players.txt!");
            return 0;
        }
    }
    
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
                        p.set("cash", getSetting("cash"));
                    } else {
                        p.set("cash", statLine.get("cash"));
                    }
                    p.set("bank", statLine.get("bank"));
                    p.set("bankrupts", statLine.get("bankrupts"));
                    p.set("bjwinnings", statLine.get("bjwinnings"));
                    p.set("bjrounds", statLine.get("bjrounds"));
                    p.set("tpwinnings", statLine.get("tpwinnings"));
                    p.set("tprounds", statLine.get("tprounds"));
                    p.setSimple(statLine.getSimple());
                    found = true;
                    break;
                }
            }
            if (!found) {
                p.set("cash", getSetting("cash"));
                infoNewPlayer(p.getNick());
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
                    statLine.setSimple(p.isSimple());
                    found = true;
                    break;
                }
            }
            if (!found) {
                statLine = new StatFileLine(p.getNick(), p.get("cash"),
                p.get("bank"), p.get("bankrupts"),
                p.get("bjwinnings"), p.get("bjrounds"),
                p.get("tpwinnings"), p.get("tprounds"),
                p.isSimple());
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
        int cash, bank, bankrupts, bjwinnings, bjrounds, tpwinnings, tprounds;
        boolean simple;
        
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
            simple = Boolean.parseBoolean(st.nextToken());
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
    public void burnCard(){
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
    public void showReloadSettings() {
        bot.sendMessage(channel, iniFile+" has been reloaded.");
    }
    /**
     * Outputs all of a player's data relevant to this game.
     * Sends a message to a player the list of statistics separated by '|'
     * character.
     * 
     * @param nick the player's nick
     */
    public void showPlayerAllStats(String nick){
        int cash = getPlayerStat(nick, "cash");
        int bank = getPlayerStat(nick, "bank");
        int net = getPlayerStat(nick, "netcash");
        int bankrupts = getPlayerStat(nick, "bankrupts");
        int winnings = 0;
        int rounds = 0;
        if (gameName.equals("Blackjack")){
            winnings = getPlayerStat(nick, "bjwinnings");
            rounds = getPlayerStat(nick, "bjrounds");
        } else if (gameName.equals("Texas Hold'em Poker")){
            winnings = getPlayerStat(nick, "tpwinnings");
            rounds = getPlayerStat(nick, "tprounds");
        }
        if (cash != Integer.MIN_VALUE) {
            bot.sendMessage(channel, nick+" | Cash: $"+formatNumber(cash)+" | Bank: $"+
                    formatNumber(bank)+" | Net: $"+formatNumber(net)+" | Bankrupts: "+
                    formatNumber(bankrupts)+" | Winnings: $"+formatNumber(winnings)+
                    " | Rounds: "+formatNumber(rounds));
        } else {
            bot.sendMessage(channel, "No data found for " + nick + ".");
        }
    }
    /**
     * Outputs the a player's rank given a nick and stat name.
     * 
     * @param nick player's nick
     * @param stat the stat name
     */
    public void showPlayerRank(String nick, String stat){
        if (getPlayerStat(nick, "exists") != 1){
            bot.sendMessage(channel, "No data found for "+nick+".");
            return;
        }
        
        int highIndex, rank = 0;
        try {
            ArrayList<StatFileLine> statList = new ArrayList<StatFileLine>();
            loadPlayerFile(statList);
            ArrayList<String> nicks = new ArrayList<String>();
            ArrayList<Integer> test = new ArrayList<Integer>();
            int length = statList.size();
            String line = Colors.BLACK + ",08";
            
            for (int ctr = 0; ctr < statList.size(); ctr++) {
                nicks.add(statList.get(ctr).getNick());
            }
            
            if (stat.equals("cash")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add(statList.get(ctr).get(stat));
                }
                line += "Cash: ";
            } else if (stat.equals("bank")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add(statList.get(ctr).get(stat));
                }
                line += "Bank: ";
            } else if (stat.equals("bankrupts")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add(statList.get(ctr).get(stat));
                }
                line += "Bankrupts: ";
            } else if (stat.equals("net") || stat.equals("netcash")) {
                for (int ctr = 0; ctr < nicks.size(); ctr++) {
                    test.add(statList.get(ctr).get("netcash"));
                }
                line += "Net Cash: ";
            } else if (stat.equals("winnings")){
                if (gameName.equals("Blackjack")){
                    for (int ctr = 0; ctr < statList.size(); ctr++) {
                        test.add(statList.get(ctr).get("bjwinnings"));
                    }
                    line += "Blackjack Winnings: ";
                } else if (gameName.equals("Texas Hold'em Poker")){
                    for (int ctr = 0; ctr < statList.size(); ctr++) {
                        test.add(statList.get(ctr).get("tpwinnings"));
                    }
                    line += "Texas Hold'em Winnings: ";
                }
            } else if (stat.equals("rounds")) {
                if (gameName.equals("Blackjack")){
                    for (int ctr = 0; ctr < statList.size(); ctr++) {
                        test.add(statList.get(ctr).get("bjrounds"));
                    }
                    line += "Blackjack Rounds: ";
                } else if (gameName.equals("Texas Hold'em Poker")){
                    for (int ctr = 0; ctr < statList.size(); ctr++) {
                        test.add(statList.get(ctr).get("tprounds"));
                    }
                    line += "Texas Hold'em Rounds: ";
                }
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
                        line += "#" + rank + " " + Colors.WHITE + ",04 " + nick + " " + formatNumber(test.get(highIndex)) + " ";
                    } else {
                        line += "#" + rank + " " + Colors.WHITE + ",04 " + nick + " $" + formatNumber(test.get(highIndex)) + " ";
                    }
                    break;
                } else {
                    nicks.remove(highIndex);
                    test.remove(highIndex);
                }
            }
            bot.sendMessage(channel, line);
        } catch (IOException e) {
            bot.log("Error reading players.txt!");
        }
    }
    /**
     * Outputs the top N players for a given statistic.
     * Sends a message to channel with the list of players sorted by ranking.
     * 
     * @param stat the statistic used for ranking
     * @param n the length of the list up to n players
     */
    public void showTopPlayers(String stat, int n) {
        if (n < 1){
            throw new IllegalArgumentException();
        }
        
        int highIndex;
        try {
            ArrayList<StatFileLine> statList = new ArrayList<StatFileLine>();
            loadPlayerFile(statList);
            ArrayList<String> nicks = new ArrayList<String>();
            ArrayList<Integer> test = new ArrayList<Integer>();
            int length = Math.min(n, statList.size());
            String title = Colors.BLACK + ",08Top " + length;
            String list;
            
            for (int ctr = 0; ctr < statList.size(); ctr++) {
                nicks.add(statList.get(ctr).getNick());
            }
            
            if (stat.equals("cash")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add(statList.get(ctr).get(stat));
                }
                title += " Cash:";
            } else if (stat.equals("bank")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add(statList.get(ctr).get(stat));
                }
                title += " Bank:";
            } else if (stat.equals("bankrupts")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add(statList.get(ctr).get(stat));
                }
                title += " Bankrupts:";
            } else if (stat.equals("net") || stat.equals("netcash")) {
                for (int ctr = 0; ctr < nicks.size(); ctr++) {
                    test.add(statList.get(ctr).get("netcash"));
                }
                title += " Net Cash:";
            } else if (stat.equals("winnings")){
                if (gameName.equals("Blackjack")){
                    for (int ctr = 0; ctr < statList.size(); ctr++) {
                        test.add(statList.get(ctr).get("bjwinnings"));
                    }
                    title += " Blackjack Winnings:";
                } else if (gameName.equals("Texas Hold'em Poker")){
                    for (int ctr = 0; ctr < statList.size(); ctr++) {
                        test.add(statList.get(ctr).get("tpwinnings"));
                    }
                    title += " Texas Hold'em Winnings:";
                }
            } else if (stat.equals("rounds")) {
                if (gameName.equals("Blackjack")){
                    for (int ctr = 0; ctr < statList.size(); ctr++) {
                        test.add(statList.get(ctr).get("bjrounds"));
                    }
                    title += " Blackjack Rounds:";
                } else if (gameName.equals("Texas Hold'em Poker")){
                    for (int ctr = 0; ctr < statList.size(); ctr++) {
                        test.add(statList.get(ctr).get("tprounds"));
                    }
                    title += " Texas Hold'em Rounds:";
                }
            } else {
                throw new IllegalArgumentException();
            }

            list = title;
            
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
                    list += " #" + ctr + ": " + Colors.WHITE + ",04 "
                            + nicks.get(highIndex) + " " 
                            + formatNumber(test.get(highIndex)) + " "
                            + Colors.BLACK + ",08";
                } else {
                    list += " #" + ctr + ": " + Colors.WHITE + ",04 "
                            + nicks.get(highIndex) + " $"
                            + formatNumber(test.get(highIndex)) + " "
                            + Colors.BLACK + ",08";
                }
                nicks.remove(highIndex);
                test.remove(highIndex);
                if (nicks.isEmpty() || ctr == length) {
                    break;
                }
                // Output and reset after 10 players
                if (ctr % 10 == 0){
                    bot.sendMessage(channel, list);
                    list = title;
                }
            }
            bot.sendMessage(channel, list);
        } catch (IOException e) {
                bot.log("Error reading players.txt!");
        }
    }
    public void showSetting(String setting, int value){
        bot.sendMessage(channel, setting + "=" + value);
    }
    public void showUpdateSetting(String setting) {
        bot.sendMessage(channel, setting + " setting has been updated.");
    }
    public void showTurn(Player p) {
        bot.sendMessage(channel,"It's now "+p.getNickStr()+"'s turn.");
    }
    public void showJoin(Player p){
        bot.sendMessage(channel, p.getNickStr()+" has joined the game. Players: "+Colors.BOLD+getNumberJoined());
    }
    public void showLeave(Player p){
        if (!p.has("cash")){
            bot.sendMessage(channel, p.getNickStr()+" has gone bankrupt and left the game. Players: "+Colors.BOLD+getNumberJoined());
        } else {
            bot.sendMessage(channel, p.getNickStr()+" has left the game. Players: "+Colors.BOLD+getNumberJoined());
        }
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
        if (getStartCount() > 0){
            bot.sendMessage(channel, formatBold("----------") + " Starting another round of "+getGameNameStr()+" in 5 seconds... (Auto-starts: " + formatBold(getStartCount()+"") + ") " + formatBold("----------"));
        } else {
            bot.sendMessage(channel, formatBold("----------") + " Starting another round of "+getGameNameStr()+" in 5 seconds... " + formatBold("----------"));
        }
    }
    public void showEndRound(){
        bot.sendMessage(channel, formatBold("----------") + " End of " + getGameNameStr() + " round. Type .go for a new round. " + formatBold("----------"));
    }
    public void showNumDecks(){
        bot.sendMessage(channel, "This game of "+getGameNameStr()+" is using "+deck.getNumberDecks()+" deck(s) of cards.");
    }
    public void showNumCards(){
        bot.sendMessage(channel, deck.getNumberCards()+" cards left in the deck.");
    }
    public void showNumDiscards(){
        bot.sendMessage(channel, deck.getNumberDiscards()+" cards in the discard pile.");
    }
    public void showDeckEmpty() {
        bot.sendMessage(channel, "The deck is empty. Refilling with discards...");
    }
    public void showPlayers(){
        bot.sendMessage(channel, "Joined: "+getPlayerListString(joined));
    }
    public void showWaitlist(){
        bot.sendMessage(channel, "Waiting: "+getPlayerListString(waitlist));
    }
    public void showBlacklist(){
        bot.sendMessage(channel, "Bankrupt: "+getPlayerListString(blacklist));
    }
    public void showPlayerCash(String nick){
        int cash = getPlayerStat(nick, "cash");
        if (cash != Integer.MIN_VALUE){
            bot.sendMessage(channel, nick+" has $"+formatNumber(cash)+".");
        } else {
            bot.sendMessage(channel, "No data found for "+nick+".");
        }
    }
    public void showPlayerNetCash(String nick){
        int netcash = getPlayerStat(nick, "netcash");
        if (netcash != Integer.MIN_VALUE){
            bot.sendMessage(channel, nick+" has $"+formatNumber(netcash)+" in net cash.");
        } else {
            bot.sendMessage(channel, "No data found for "+nick+".");
        }
    }
    public void showPlayerBank(String nick){
        int bank = getPlayerStat(nick, "bank");
        if (bank != Integer.MIN_VALUE){
            if (bank < 0){
                bot.sendMessage(channel, nick+" has $"+formatNumber(bank)+" in debt.");
            } else {
                bot.sendMessage(channel, nick+" has $"+formatNumber(bank)+" in the bank.");
            }
        } else {
            bot.sendMessage(channel, "No data found for "+nick+".");
        }
    }
    public void showPlayerBankrupts(String nick){
        int bankrupts = getPlayerStat(nick, "bankrupts");
        if (bankrupts != Integer.MIN_VALUE){
            bot.sendMessage(channel, nick+" has gone bankrupt "+bankrupts+" time(s).");
        } else {
            bot.sendMessage(channel, "No data found for "+nick+".");
        }
    }
    public void showPlayerWinnings(String nick){
        int winnings = 0;
        if (gameName.equals("Blackjack")){
            winnings = getPlayerStat(nick, "bjwinnings");
        } else if (gameName.equals("Texas Hold'em Poker")){
            winnings = getPlayerStat(nick, "tpwinnings");
        }
        
        if (winnings != Integer.MIN_VALUE) {
            bot.sendMessage(channel, nick + " has won $" + winnings + " in " + getGameNameStr() + ".");
        } else {
            bot.sendMessage(channel, "No data found for " + nick + ".");
        }
    }
    public void showPlayerWinRate(String nick){
        double winnings = 0;
        double rounds = 0;
        if (gameName.equals("Blackjack")){
            winnings = (double) getPlayerStat(nick, "bjwinnings");
            rounds = (double) getPlayerStat(nick, "bjrounds");
        } else if (gameName.equals("Texas Hold'em Poker")){
            winnings = (double) getPlayerStat(nick, "tpwinnings");
            rounds = (double) getPlayerStat(nick, "tprounds");
        }
        
        if (rounds != Integer.MIN_VALUE) {
            if (rounds == 0){
                bot.sendMessage(channel, nick + " has not played any rounds of " + getGameNameStr() + ".");
            } else {
                bot.sendMessage(channel, nick + " has won $" + formatDecimal(winnings/rounds) + " per round in " + getGameNameStr() + ".");
            }    
        } else {
            bot.sendMessage(channel, "No data found for " + nick + ".");
        }
    }
    public void showPlayerRounds(String nick){
        int rounds = 0;
        if (gameName.equals("Blackjack")){
            rounds = getPlayerStat(nick, "bjrounds");
        } else if (gameName.equals("Texas Hold'em Poker")){
            rounds = getPlayerStat(nick, "tprounds");
        }
        
        if (rounds != Integer.MIN_VALUE) {
            bot.sendMessage(channel, nick + " has played " + rounds + " round(s) of " + getGameNameStr() + ".");
        } else {
            bot.sendMessage(channel, "No data found for " + nick + ".");
        }
    } 
    public void showPlayerDeposit(Player p, int amount){
        bot.sendMessage(channel, p.getNickStr()+" has made a deposit of $"+formatNumber(amount)+". Cash: $"+formatNumber(p.get("cash"))+". Bank: $"+formatNumber(p.get("bank"))+".");
    }    
    public void showPlayerWithdraw(Player p, int amount){
        bot.sendMessage(channel, p.getNickStr()+" has made a withdrawal of $"+formatNumber(amount)+". Cash: $"+formatNumber(p.get("cash"))+". Bank: $"+formatNumber(p.get("bank"))+".");
    }
    public void showSeparator() {
        bot.sendMessage(channel, Colors.BOLD+ "------------------------------------------------------------------");
    }
    
    /* 
     * Private messages to players.
     * These methods will all send notices/messages to the specified nick.
     */
    public void infoGameRules(String nick) {
        bot.sendNotice(nick,getGameRulesStr());
    }
    public void infoGameHelp(String nick) {
        bot.sendNotice(nick,getGameHelpStr());
    }
    public void infoGameCommands(String nick){
        bot.sendNotice(nick,getGameNameStr()+" commands:");
        bot.sendNotice(nick,getGameCommandStr());
    }
    public void infoNewPlayer(String nick){
        bot.sendNotice(nick, "Welcome to "+getGameNameStr()+"! Here's $"+formatNumber(getSetting("cash"))+" to get you started!");
    }
    public void infoNewNick(String nick){
        bot.sendNotice(nick, "Welcome to "+getGameNameStr()+"! For help, type .ghelp!");
    }
    public void infoMaxPlayers(String nick){
        bot.sendNotice(nick, "Unable to join. The maximum number of players has been reached.");
    }
    public void infoAlreadyJoined(String nick){
        bot.sendNotice(nick, "You have already joined!");
    }
    public void infoNotJoined(String nick){
        bot.sendNotice(nick, "You are not currently joined!");
    }
    public void infoJoinWaitlist(String nick){
        bot.sendNotice(nick, "You have joined the waitlist and will be automatically added next round.");
    }
    public void infoLeaveWaitlist(String nick){
        bot.sendNotice(nick, "You have left the waitlist and will not be automatically added next round.");
    }
    public void infoAlreadyWaitlisted(String nick){
        bot.sendNotice(nick, "You have already joined the waitlist!");
    }
    public void infoBlacklisted(String nick){
        bot.sendNotice(nick, "You have gone bankrupt. Please wait for a loan to join again.");
    }
    public void infoWaitRoundEnd(String nick){
        bot.sendNotice(nick, "A round is in progress! Wait for the round to end.");
    }
    public void infoRoundStarted(String nick){
        bot.sendNotice(nick, "A round is in progress!");
    }
    public void infoNotStarted(String nick){
        bot.sendNotice(nick, "No round in progress!");
    }
    public void infoNotBetting(String nick){
        bot.sendNotice(nick, "No betting in progress!");
    }
    public void infoNotTurn(String nick){
        bot.sendNotice(nick, "It's not your turn!");
    }
    public void infoNoCards(String nick){
        bot.sendNotice(nick, "No cards have been dealt yet!");
    }
    public void infoOpsOnly(String nick){
        bot.sendNotice(nick, "This command may only be used by channel Ops.");
    }
    public void infoNickChange(String nick){
        bot.sendNotice(nick, "You have changed nicks while joined. Your old nick will be removed " +
                        "and your new nick will be joined, if possible.");
    }
    public void infoNoTransaction(String nick){
        bot.sendNotice(nick, "Invalid transaction!");
    }
    public void infoNoWithdrawal(String nick){
        bot.sendNotice(nick, "Your bankroll contains insufficient funds for this transaction.");
    }
    public void infoNoDepositCash(String nick){
        bot.sendNotice(nick, "You do not have that much cash. Try again.");
    }
    public void infoNoDepositBankrupt(String nick){
        bot.sendNotice(nick, "You cannot go bankrupt making a deposit. Try again.");
    }
    public void infoPaymentTooLow(String nick){
        bot.sendNotice(nick, "Minimum payment is $1. Try again.");
    }
    public void infoPaymentTooHigh(String nick, int max){
        bot.sendNotice(nick, "Your outstanding debt is only $"+formatNumber(max)+". Try again.");
    }
    public void infoPaymentWillBankrupt(String nick){
        bot.sendNotice(nick, "You cannot go bankrupt trying to pay off your debt. Try again.");
    }
    public void infoInsufficientFunds(String nick){
        bot.sendNotice(nick, "Insufficient funds.");
    }
    public void infoNickNotFound(String nick, String searchNick){
        bot.sendNotice(nick, searchNick + " was not found.");
    }
    public void infoNoParameter(String nick){
        bot.sendNotice(nick, "Parameter(s) missing!");
    }
    public void infoBadParameter(String nick){
        bot.sendNotice(nick,"Bad parameter(s). Try again.");
    }
    public void infoBetTooLow(String nick){
        bot.sendNotice(nick, "Minimum bet is $"+formatNumber(getSetting("minbet"))+". Try again.");
    }
    public void infoBetTooHigh(String nick, int max){
        bot.sendNotice(nick, "Maximum bet is $"+formatNumber(max)+". Try again.");
    }
    public void infoAutoWithdraw(String nick, int amount){
        bot.sendNotice(nick, "You make a withdrawal of $" + formatNumber(amount) + " to replenish your empty stack.");
    }
    public void infoPlayerBankrupt(String nick, int penalty){
        if ((getSetting("respawn")+penalty) % 60 == 0){
            bot.sendNotice(nick, "You've lost all your money. Please wait " + (getSetting("respawn")+penalty)/60 + " minute(s) for a loan.");
        } else {
            bot.sendNotice(nick, "You've lost all your money. Please wait " + String.format("%.1f", (getSetting("respawn")+penalty)/60.) + " minutes for a loan.");
        }
    }
    public void infoPlayerIdleWarning(Player p){
        if (p.isSimple()) {
            bot.sendNotice(p.getNick(), p.getNickStr()
                + ": You will idle out in " + (getSetting("idle") - getSetting("idlewarning")) 
                + " seconds. Please make your move.");
        } else {
            bot.sendMessage(p.getNick(), p.getNickStr()
                + ": You will idle out in " + (getSetting("idle") - getSetting("idlewarning")) 
                + " seconds. Please make your move.");
        }
    }
    public void infoGameCommandHelp(String nick, String command){
        bot.sendNotice(nick, getCommandHelp(command));
    }
    
    /* Reveals cards from the card deck */
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
            cardStr += "-"+(cardIndex+numOut-1)+": ";
            n -= numOut;
            for (int ctr=cardIndex; ctr < cardIndex+numOut; ctr++){
                cardStr += tCards.get(ctr)+" ";
            }
            cardIndex+=numOut;
            bot.sendNotice(nick, cardStr.substring(0, cardStr.length()-1)+Colors.NORMAL);
        }
    }
    
    /* Formatted strings */
    public String getGameNameStr(){
        return Colors.BOLD + gameName + Colors.BOLD;
    }
    public String getGameHelpStr() {
        return "For a list of game commands, type .gcommands. For house rules, type .grules. " +
                "For help on individual commands, type .ghelp <command>.";
    }
    abstract public String getGameRulesStr();
    abstract public String getGameCommandStr();
    public static String formatDecimal(double n){
        return String.format("%.2f", n);
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
    protected static String getPlayerListString(ArrayList<? extends Player> playerList){
        String outStr;
        int size = playerList.size();
        if (size == 0){
            outStr = Colors.BOLD+"0"+Colors.BOLD+" players";
        } else if (size == 1){
            outStr = Colors.BOLD+"1"+Colors.BOLD+" player: "+playerList.get(0).getNick();
        } else {
            outStr = Colors.BOLD+size+Colors.BOLD+" players: ";
            for (int ctr=0; ctr < size; ctr++){
                if (ctr == size-1){
                    outStr += playerList.get(ctr).getNick();
                } else {
                    outStr += playerList.get(ctr).getNick()+", ";
                }
            }
        }
        return outStr;
    }
    protected static String getListString(ArrayList<String> stringList){
        String outStr;
        int size = stringList.size();
        if (size == 0){
            outStr = Colors.BOLD+"0"+Colors.BOLD+" players";
        } else if (size == 1){
            outStr = Colors.BOLD+"1"+Colors.BOLD+" player: " + stringList.get(0);
        } else {
            outStr = Colors.BOLD+size+Colors.BOLD+" players: ";
            for (int ctr=0; ctr < size; ctr++){
                if (ctr == size-1){
                    outStr += stringList.get(ctr);
                } else {
                    outStr += stringList.get(ctr) + ", ";
                }
            }
        }
        return outStr;
    }

    /**
     * Searches the help file for data on the specified command.
     * @param command
     * @return the help data for the command
     */
    protected String getCommandHelp(String command){
        try {
            BufferedReader in = new BufferedReader(new FileReader(getHelpFile()));
            StringTokenizer st;
            String c,d="";
            boolean found = false;
            while (in.ready()){
                st = new StringTokenizer(in.readLine(), "=");
                if (st.countTokens() == 2){
                    c = st.nextToken();
                    d = st.nextToken();
                    if (c.equals(command)){
                        found = true;
                        break;
                    }
                }
            }
            in.close();
            if (found){
                return d;
            } else {
                return "Help for \'"+command+"\' not found!";
            }
        } catch (IOException e) {
            bot.log("Error reading from "+getHelpFile()+"!");
            return "Error reading from "+getHelpFile()+"!";
        }
    }
}