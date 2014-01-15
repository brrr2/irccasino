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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import org.pircbotx.*;
import irccasino.*;
import irccasino.cardgame.CardGame;
import irccasino.cardgame.Hand;
import irccasino.cardgame.Player;

/**
 * Class for IRC Blackjack.
 * @author Yizhe Shen
 */
public class Blackjack extends CardGame {
    
    private BlackjackPlayer dealer;
    private ArrayList<HouseStat> houseStatsList;
    private IdleShuffleTask idleShuffleTask;
    private HouseStat house;
    // In-game properties
    private boolean betting, insuranceBets;

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
        super(parent, commChar, gameChannel);
        name = "blackjack";
        iniFile = customINI;
        helpFile = "blackjack.help";
        strFile = "strlib.txt";
        loadStrLib(strFile);
        loadHelp(helpFile);
        dealer = new BlackjackPlayer("Dealer", "");
        houseStatsList = new ArrayList<HouseStat>();
        loadGameStats();
        initialize();
        loadIni();
        idleShuffleTask = null;
        showMsg(getMsg("game_start"), getGameNameStr());
    }

    @Override
    public void processCommand(User user, String command, String[] params){
        String nick = user.getNick();
        String host = user.getHostmask();

        /* Check if it's a common command */
        super.processCommand(user, command, params);
        
        /* Parsing commands from the channel */
        if (command.equals("start") || command.equals("go")){
            if (isStartAllowed(nick)) {
                if (params.length > 0){
                    try {
                        startCount = Math.min(get("autostarts") - 1, Integer.parseInt(params[0]) - 1);
                    } catch (NumberFormatException e) {
                        // Do nothing and proceed
                    }
                }
                cancelIdleShuffleTask();
                inProgress = true;
                showStartRound();
                setStartRoundTask();
            }
        } else if (command.equals("bet") || command.equals("b")) {
            if (isStage1PlayerTurn(nick)){
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
        } else if (command.equals("allin") || command.equals("a")){
            if (isStage1PlayerTurn(nick)){
                bet(currentPlayer.get("cash"));
            }
        } else if (command.equals("hit") || command.equals("h")) {
            if (isStage2PlayerTurn(nick)){
                hit();
            }
        } else if (command.equals("stay") || command.equals("stand") || command.equals("sit")) {
            if (isStage2PlayerTurn(nick)){
                stay();
            }
        } else if (command.equals("doubledown") || command.equals("dd")) {
            if (isStage2PlayerTurn(nick)){
                doubleDown();
            }
        } else if (command.equals("surrender") || command.equals("surr")) {
            if (isStage2PlayerTurn(nick)){
                surrender();
            }
        } else if (command.equals("insure")) {
            if (isStage2PlayerTurn(nick)){
                if (params.length > 0){
                    try {
                        insure(Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equals("split")) {
            if (isStage2PlayerTurn(nick)){
                split();
            }
        } else if (command.equals("table")) {
            if (isStage2(nick)){
                showTableHands(false);
            }
        } else if (command.equals("sum")) {
            if (isStage2(nick)){
                BlackjackPlayer p = (BlackjackPlayer) findJoined(nick);
                informPlayer(p.getNick(), getMsg("bj_hand_sum"), p.getHand().calcSum());
            }
        } else if (command.equals("hand")) {
            if (isStage2(nick)){
                BlackjackPlayer p = (BlackjackPlayer) findJoined(nick);
                informPlayer(p.getNick(), getMsg("bj_hand"), p.getHand(), p.getHand().getBet());
            }
        } else if (command.equals("allhands")) {
            informPlayer(nick, "This command is not implemented.");
        } else if (command.equals("turn")) {
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else if (!inProgress) {
                informPlayer(nick, getMsg("no_start"));
            } else {
                BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
                if (p.hasSplit()){
                    showTurn(p, p.get("currentindex") + 1);
                } else {
                    showTurn(p, 0);
                }
            }
            /* Contributed by Yky */
        } else if (command.equals("zc") || (command.equals("zen"))) {
            if (isCountAllowed(nick)){
                showMsg(getMsg("bj_zen"), getZen());
            }
            /* Contributed by Yky */
        } else if (command.equals("hc") || (command.equals("hilo"))) {
            if (isCountAllowed(nick)){
                showMsg(getMsg("bj_hilo"), getHiLo());
            }
            /* Contributed by Yky */
        } else if (command.equals("rc") || (command.equals("red7"))) {
            if (isCountAllowed(nick)){
                showMsg(getMsg("bj_red7"), getRed7());
            }
        } else if (command.equals("count") || command.equals("c")){
            if (isCountAllowed(nick)){
                showMsg(getMsg("bj_count"), deck.getNumberCards(), getHiLo(), getRed7(), getZen());
            }
        } else if (command.equals("numcards") || command.equals("ncards")) {
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else if (inProgress) {
                informPlayer(nick, getMsg("wait_round_end"));
            } else {
                showMsg(getMsg("bj_num_cards"), deck.getNumberCards());
            }
        } else if (command.equals("numdiscards") || command.equals("ndiscards")) {
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else if (inProgress) {
                informPlayer(nick, getMsg("wait_round_end"));
            } else {
                showMsg(getMsg("num_discards"), deck.getNumberDiscards());
            }
        } else if (command.equals("numdecks") || command.equals("ndecks")) {
            showMsg(getMsg("num_decks"), getGameNameStr(), deck.getNumberDecks());
        } else if (command.equals("players")) {
            showMsg(getMsg("players"), getPlayerListString(joined));
        } else if (command.equals("house")) {
            if (inProgress) {
                informPlayer(nick, getMsg("wait_round_end"));
            } else {
                if (params.length > 0){
                    try {
                        showHouseStat(Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    showHouseStat(get("decks"));
                }
            }
        /* Op commands */
        } else if (command.equals("fstart") || command.equals("fgo")){
            if (isForceStartAllowed(user,nick)){
                cancelIdleShuffleTask();
                inProgress = true;
                showStartRound();
                setStartRoundTask();
            }
        } else if (command.equals("fstop")){
            // Use only as last resort. Data will be lost.
            if (isForceStopAllowed(user,nick)){
                BlackjackPlayer p;
                cancelIdleOutTask();
                for (int ctr = 0; ctr < joined.size(); ctr++) {
                    p = (BlackjackPlayer) joined.get(ctr);
                    resetPlayer(p);
                }
                resetGame();
                showMsg(getMsg("end_round"), getGameNameStr(), commandChar);
                inProgress = false;
            }
        } else if (command.equals("fb") || command.equals("fbet")){
            if (isForceBetAllowed(user, nick)){
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
        } else if (command.equals("fallin") || command.equals("fa")){
            if (isForceBetAllowed(user, nick)){
                bet(currentPlayer.get("cash"));
            }
        } else if (command.equals("fhit") || command.equals("fh")) {
            if (isForcePlayAllowed(user, nick)){
                hit();
            }
        } else if (command.equals("fstay") || command.equals("fstand") || command.equals("fsit")) {
            if (isForcePlayAllowed(user, nick)){
                stay();
            }
        } else if (command.equals("fdoubledown") || command.equals("fdd")) {
            if (isForcePlayAllowed(user, nick)){
                doubleDown();
            }
        } else if (command.equals("fsurrender") || command.equals("fsurr")) {
            if (isForcePlayAllowed(user, nick)){
                surrender();
            }
        } else if (command.equals("fsplit")) {
            if (isForcePlayAllowed(user, nick)){
                split();
            }
        } else if (command.equals("finsure")) {
            if (isForcePlayAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        insure(Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equals("shuffle")){
            if (isOpCommandAllowed(user, nick)){
                cancelIdleShuffleTask();
                shuffleShoe();
            }
        } else if (command.equals("reload")){
            if (isOpCommandAllowed(user, nick)){
                cancelIdleShuffleTask();
                loadIni();
                cmdMap.clear();
                opCmdMap.clear();
                aliasMap.clear();
                msgMap.clear();
                loadHelp(helpFile);
                loadStrLib(strFile);
                showMsg(getMsg("reload"));
            }
        } else if (command.equals("test1")){
            // 1. Tests the dealer playing algorithm and underlying calculations.
            if (isOpCommandAllowed(user, nick)){
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
    }

    /* Game settings management */
    @Override
    protected void set(String setting, int value) {
        super.set(setting, value);
        if (setting.equals("decks")) {
            cancelIdleShuffleTask();
            deck = new CardDeck(get("decks"));
            deck.shuffleCards();
            house = getHouseStat(get("decks"));
            if (house == null) {
                house = new HouseStat(get("decks"), 0, 0);
                houseStatsList.add(house);
            }
        }
    }
    @Override
    protected final void initialize() {
        super.initialize();
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
        // In-game properties
        betting = true;
        insuranceBets = false;
    }
    @Override
    protected final void loadIni() {
        super.loadIni();
        cancelIdleShuffleTask();
        deck = new CardDeck(get("decks"));
        deck.shuffleCards();
        house = getHouseStat(get("decks"));
        if (house == null) {
            house = new HouseStat(get("decks"), 0, 0);
            houseStatsList.add(house);
        }
    }
    @Override
    protected void saveIniFile() {
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(iniFile)));
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
            out.close();
        } catch (IOException e) {
            manager.log("Error creating " + iniFile + "!");
        }
    }

    /* Game stats management */
    public final void loadGameStats() {
        try {
            BufferedReader in = new BufferedReader(new FileReader("housestats.txt"));
            String str;
            int ndecks, nrounds, cash;
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
                        ndecks = Integer.parseInt(st.nextToken());
                        nrounds = Integer.parseInt(st.nextToken());
                        cash = Integer.parseInt(st.nextToken());
                        houseStatsList.add(new HouseStat(ndecks, nrounds, cash));
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
    public void saveGameStats() {
        boolean found = false;
        int index = 0;
        ArrayList<String> lines = new ArrayList<String>();
        try {
            BufferedReader in = new BufferedReader(new FileReader("housestats.txt"));
            String str;
            while (in.ready()) {
                //Add all lines until we find blackjack lines
                str = in.readLine();
                lines.add(str);
                if (str.startsWith("#blackjack")) {
                    found = true;
                    /* Store the index where blackjack stats go so they can be 
                     * overwritten. */
                    index = lines.size();
                    //Skip existing blackjack lines but add all the rest
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
            lines.add("#blackjack");
            index = lines.size();
        }
        for (int ctr = 0; ctr < houseStatsList.size(); ctr++) {
            lines.add(index, houseStatsList.get(ctr).toFileString());
        }
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
    
    /**
     * Returns the house statistics for a given shoe size.
     * @param numDecks shoe size in number of decks
     * @return the house stats
     */
    private HouseStat getHouseStat(int numDecks) {
        for (HouseStat hs : houseStatsList) {
            if (hs.get("decks") == numDecks) {
                return hs;
            }
        }
        return null;
    }
    
    /**
     * Calculates the total number of rounds played by all players.
     * @return the total number of rounds
     */
    private int getTotalRounds(){
        int total=0;
        for (HouseStat hs : houseStatsList) {
            total += hs.get("rounds");
        }
        return total;
    }
    
    /**
     * Calculates the total amount won by the house.
     * @return the total amount won by the house
     */
    private int getTotalHouse(){
        int total=0;
        for (HouseStat hs : houseStatsList) {
            total += hs.get("cash");
        }
        return total;
    }
    
    /* Game management methods */
    @Override
    public void addPlayer(String nick, String host) {
        addPlayer(new BlackjackPlayer(nick, host));
    }
    @Override
    public void addWaitlistPlayer(String nick, String host) {
        Player p = new BlackjackPlayer(nick, host);
        waitlist.add(p);
        informPlayer(p.getNick(), getMsg("join_waitlist"));
    }
    @Override
    public void leave(String nick) {
        // Check if the nick is even joined
        if (isJoined(nick)){
            BlackjackPlayer p = (BlackjackPlayer) findJoined(nick);
            // Check if a round is in progress
            if (inProgress) {
                // If in the betting or post-start wait phase
                if (betting || currentPlayer == null){
                    if (p == currentPlayer){
                        cancelIdleOutTask();
                        currentPlayer = getNextPlayer();
                        removeJoined(p);
                        if (currentPlayer == null) {
                            betting = false;
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
                            p.set("quit", 1);
                            informPlayer(p.getNick(), getMsg("remove_end_round"));
                        } else {
                            removeJoined(p);
                        }
                    }
                // Check if it is already in the endRound stage
                } else if (roundEnded){
                    p.set("quit", 1);
                    informPlayer(p.getNick(), getMsg("remove_end_round"));
                // If in the card-playing phase
                } else {
                    p.set("quit", 1);
                    informPlayer(p.getNick(), getMsg("remove_end_round"));
                    if (p == currentPlayer){
                        stay();
                    }
                }
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
    @Override
    public void startRound() {
        if (joined.size() > 0) {
            showMsg(getMsg("players"), getPlayerListString(joined));
            betting = true;
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
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        if (p.get("currentindex") < p.getNumberHands() - 1) {
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
        roundEnded = true;
        BlackjackPlayer p;
        BlackjackHand dHand;

        if (joined.size() >= 1) {
            house.increment("rounds");
            // Make dealer decisions
            if (needDealerPlay()) {
                showTurn(dealer, 0);
                dHand = dealer.getHand();
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
            /* Clean-up tasks
             * 1. Increment the number of rounds played for player
             * 2. Remove players who have gone bankrupt and set respawn timers
             * 3. Remove players who have quit mid-round
             * 4. Save player data
             * 5. Reset the player
             */
            for (int ctr = 0; ctr < joined.size(); ctr++) {
                p = (BlackjackPlayer) joined.get(ctr);
                p.increment("bjrounds");

                // Bankrupts
                if (!p.has("cash")) {
                    // Make a withdrawal if the player has a positive bankroll
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
                    // Give penalty to players with no cash in their bankroll
                    } else {
                        p.increment("bankrupts");
                        blacklist.add(p);
                        removeJoined(p);
                        setRespawnTask(p);
                        ctr--;
                    }
                // Quitters
                } else if (p.has("quit")) {
                    removeJoined(p.getNick());
                    ctr--;
                // Remaining players
                } else {
                    savePlayerData(p);
                }
                resetPlayer(p);
            }
            saveGameStats();
        } else {
            showMsg(getMsg("no_players"));
        }
        resetGame();
        showMsg(getMsg("end_round"), getGameNameStr(), commandChar);
        mergeWaitlist();
        // Check if any auto-starts remaining
        if (startCount > 0){
            startCount--;
            if (!inProgress) {
                if (joined.size() > 0) {
                    inProgress = true;
                    showStartRound();
                    setStartRoundTask();
                } else {
                    startCount = 0;
                    setIdleShuffleTask();
                }
            }
        } else if (deck.getNumberDiscards() > 0) {
            setIdleShuffleTask();
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
        houseStatsList.clear();
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
        inProgress = false;
        roundEnded = false;
        insuranceBets = false;
        betting = true;
        discardPlayerHand(dealer);
        currentPlayer = null;
    }
    /**
     * Resets a BlackjackPlayer back to default values.
     * This method is called at the end of a Blackjack round for each player in
     * preparation for the following round.
     * 
     * @param p the player
     */
    private void resetPlayer(BlackjackPlayer p) {
        discardPlayerHand(p);
        p.clear("currentindex");
        p.clear("initialbet");
        p.clear("quit");
        p.clear("surrender");
        p.clear("insurebet");
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
    
    /* Game command logic checking methods */
    /**
     * Checks if a round is allowed to start by the player.
     * @param nick the player who issued a start command
     * @return true if a new round can be started
     */
    private boolean isStartAllowed(String nick){
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (inProgress) {
            informPlayer(nick, getMsg("round_started"));
        } else if (joined.size() < 1) {
            showMsg(getMsg("no_players"));
        } else {
            return true;
        }
        return false;
    }
    
    /**
     * Checks if the player can make a bet.
     * @param nick the player
     * @return true if the player can make a bet
     */
    private boolean isStage1PlayerTurn(String nick){
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!inProgress) {
            informPlayer(nick, getMsg("no_start"));
        } else if (!betting) {
            informPlayer(nick, getMsg("no_betting"));
        } else if (currentPlayer != findJoined(nick)) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else {
            return true;
        }
        return false;
    }
    
    /**
     * Checks if the round is past the betting stage.
     * @param nick the player
     * @return true if past the betting stage
     */
    private boolean isStage2(String nick){
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (!inProgress) {
            informPlayer(nick, getMsg("no_start"));
        } else if (betting) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            return true;
        }
        return false;
    }
    
    /**
     * Checks if the player can make a stage 2 command.
     * @param nick the player
     * @return true if the player can make a stage 2 command
     */
    private boolean isStage2PlayerTurn(String nick){
        if (!isStage2(nick)){
        } else if (!(currentPlayer == findJoined(nick))) {
            informPlayer(nick, getMsg("wrong_turn"));
        } else {
            return true;
        }
        return false;
    }
    
    /**
     * Check if the player can force start a round.
     * Op command.
     * @param user the user object of the player
     * @param nick the player
     * @return true if a force start is allowed
     */
    private boolean isForceStartAllowed(User user, String nick){
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (inProgress) {
            informPlayer(nick, getMsg("round_started"));
        } else if (joined.size() < 1) {
            showMsg(getMsg("no_players"));
        } else {
            return true;
        }
        return false;
    }
    
    /**
     * Check if the player can force stop a round.
     * Op command.
     * @param user the user object of the player
     * @param nick the player
     * @return true if a force stop is allowed
     */
    private boolean isForceStopAllowed(User user, String nick){
        if (!channel.isOp(user)){
            informPlayer(nick, getMsg("ops_only"));
        } else if (!inProgress){
            informPlayer(nick, getMsg("no_start"));
        } else {
            return true;
        }
        return false;
    }
    
    /**
     * Check if the player can make a force play.
     * Op command.
     * @param user the user object of the player
     * @param nick the player
     * @return true if a force play is allowed
     */
    private boolean isForcePlayAllowed(User user, String nick){
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!inProgress) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("no_force_play"));
        } else if (betting) {
            informPlayer(nick, getMsg("no_cards"));
        } else {
            return true;
        }
        return false;
    }
    
    /**
     * Check if the player can make a force bet.
     * Op command.
     * @param user the user obejct of the player
     * @param nick the player
     * @return true if a force bet can be made
     */
    private boolean isForceBetAllowed(User user, String nick){
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!inProgress) {
            informPlayer(nick, getMsg("no_start"));
        } else if (currentPlayer == null) {
            informPlayer(nick, getMsg("no_force_play"));
        } else if (!betting) {
            informPlayer(nick, getMsg("no_betting"));
        } else {
            return true;
        }
        return false;
    }
    
    /**
     * Check if the player can use a card-counting command.
     * @param nick the player
     * @return true if a card-counting command can be made
     */
    private boolean isCountAllowed(String nick){
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (inProgress) {
            informPlayer(nick, getMsg("wait_round_end"));
        } else if (!has("count")) {
            informPlayer(nick, getMsg("count_disabled"));
        } else {
            return true;
        }
        return false;
    }

    /* Card management methods for Blackjack */
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
            h.setBet(p.get("initialbet"));
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

    /* Blackjack gameplay methods */
    
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
        if (amount > p.get("cash")) {
            informPlayer(p.getNick(), getMsg("bet_too_high"), p.get("cash"));
            setIdleOutTask();
        // Check if the amount is less than minimum bet
        } else if (amount < get("minbet") && amount < p.get("cash")) {
            informPlayer(p.getNick(), getMsg("bet_too_low"), get("minbet"));
            setIdleOutTask();
        } else {
            p.set("initialbet", amount);
            p.add("cash", -1 * amount);
            p.add("bjwinnings", -1 * amount);
            house.add("cash", amount);
            showMsg(getMsg("bj_bet"), p.getNickStr(), p.get("initialbet"), p.get("cash"));
            currentPlayer = getNextPlayer();
            if (currentPlayer == null) {
                betting = false;
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
        } else if (p.get("initialbet") > p.get("cash")) {
            informPlayer(p.getNick(), getMsg("insufficient_funds"));
            setIdleOutTask();
        } else {			
            p.add("cash", -1 * h.getBet());
            p.add("bjwinnings", -1 * h.getBet());
            house.add("cash", h.getBet());
            h.addBet(h.getBet());
            showMsg(getMsg("bj_dd"), p.getNickStr(), h.getBet(), p.get("cash"));
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
        if (p.hasSplit()){
            informPlayer(p.getNick(), getMsg("no_surr_split"));
            setIdleOutTask();
        } else if (h.hasHit()) {
            informPlayer(p.getNick(), getMsg("no_surr"));
            setIdleOutTask();
        } else {
            p.add("cash", calcHalf(p.get("initialbet")));
            p.add("bjwinnings", calcHalf(p.get("initialbet")));
            house.add("cash", -1 * calcHalf(p.get("initialbet")));
            p.set("surrender", 1);
            showMsg(getMsg("bj_surr"), p.getNickStr(), p.get("cash"));
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
        } else if (p.hasSplit()){
            informPlayer(p.getNick(), getMsg("no_insure_has_split"));
        } else if (amount > p.get("cash")) {
            informPlayer(p.getNick(), getMsg("insufficient_funds"));
        } else if (amount > calcHalf(p.get("initialbet"))) {
            informPlayer(p.getNick(), getMsg("insure_bet_too_high"), calcHalf(p.get("initialbet")));
        } else if (amount <= 0) {
            informPlayer(p.getNick(), getMsg("insure_bet_too_low"));
        } else {
            insuranceBets = true;
            p.set("insurebet", amount);
            p.add("cash", -1 * amount);
            p.add("bjwinnings", -1 * amount);
            house.add("cash", amount);
            showMsg(getMsg("bj_insure"), p.getNickStr(), p.get("insurebet"), p.get("cash"));
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
        } else if (p.get("cash") < cHand.getBet()) {
            informPlayer(p.getNick(), getMsg("insufficient_funds"));
            setIdleOutTask();
        } else {
            p.add("cash", -1 * cHand.getBet());
            p.add("bjwinnings", -1 * cHand.getBet());
            house.add("cash", cHand.getBet());
            p.splitHand();
            dealCard(cHand);
            nHand = p.getHand(p.get("currentindex") + 1);
            dealCard(nHand);
            nHand.setBet(cHand.getBet());
            showSplitHands(p);
            showMsg(getMsg("separator"));
            quickEval();
        }
    }

    /* Blackjack behind-the-scenes methods */
    /**
     * Determines what to do when the action falls to a new player/hand
     */
    private void quickEval() {
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        if (p.hasSplit()) {
            showTurn(p, p.get("currentindex") + 1);
        } else {
            showTurn(p, 0);
        }
        if (p.has("quit")){
            stay();
        } else {
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
        return 3 * p.get("insurebet");
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
                if (!h.isBust() && !p.hasSurrendered() && !h.isBlackjack()) {
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
        p.add("bjwinnings", payout);
        house.add("cash", -1 * payout);
    }
    
    /**
     * Pays insurance winnings.
     * @param p the player with an insurance bet
     */
    private void payPlayerInsurance(BlackjackPlayer p){
        if (dealer.getHand().isBlackjack()) {
            p.add("cash", calcInsurancePayout(p));
            p.add("bjwinnings", calcInsurancePayout(p));
            house.add("cash", -1 * calcInsurancePayout(p));
        }
    }

    /* Card-counting methods */
    /**
     * Calculates the Zen count.
     * Contributors: Yky, brrr 
     */
    private int getZen() {
        int zenCount = 0;
        String face;
        ArrayList<Card> discards = deck.getDiscards();
        for (int i = 0; i < deck.getNumberDiscards(); i++) {
            face = discards.get(i).getFace();
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
        ArrayList<Card> discards = deck.getDiscards();
        for (int i = 0; i < deck.getNumberDiscards(); i++) {
            face = discards.get(i).getFace();
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
        ArrayList<Card> discards = deck.getDiscards();
        for (int i = 0; i < deck.getNumberDiscards(); i++) {
            face = discards.get(i).getFace();
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
    
    @Override
    public int getTotalPlayers(){
        try {
            ArrayList<StatFileLine> statList = new ArrayList<StatFileLine>();
            loadPlayerFile(statList);
            int total = 0, numLines = statList.size();
            
            for (int ctr = 0; ctr < numLines; ctr++){
                if (statList.get(ctr).has("bjrounds")){
                    total++;
                }
            }
            return total;
        } catch (IOException e){
            manager.log("Error reading players.txt!");
            return 0;
        }
    }
    
    /* Channel message output methods for Blackjack */
    /**
     * Shows house stats for a given shoe size.
     * @param n the number of decks in the shoe
     */
    public void showHouseStat(int n) {
        HouseStat hs = getHouseStat(n);
        if (hs != null) {
            showMsg(hs.toString());
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
        if (betting) {
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
        showMsg(getMsg("bj_split"), p.getNickStr(), p.getNickStr());
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
        if (p.hasSplit()) {
            showPlayerHand(p, h, p.get("currentindex") + 1, false);
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
                if (p.hasSplit()) {
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
                if (!p.hasSurrendered()){
                    payPlayer(p,h);
                }
                if (p.hasSplit()) {
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
        if (p.hasSurrendered()) {
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
    public void showPlayerWinnings(String nick){
        int winnings = getPlayerStat(nick, "bjwinnings");
        if (winnings != Integer.MIN_VALUE) {
            showMsg(getMsg("player_winnings"), formatNoPing(nick), winnings, getGameNameStr());
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }
    
    @Override
    public void showPlayerWinRate(String nick){
        double winnings = (double) getPlayerStat(nick, "bjwinnings");
        double rounds = (double) getPlayerStat(nick, "bjrounds");
        
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
        int rounds = getPlayerStat(nick, "bjrounds");
        
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
        int winnings = getPlayerStat(nick, "bjwinnings");
        int rounds = getPlayerStat(nick, "bjrounds");
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
                    test.add((double) statList.get(ctr).get("bjwinnings"));
                }
                line += "Blackjack Winnings: ";
            } else if (stat.equals("rounds")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("bjrounds"));
                }
                line += "Blackjack Rounds: ";
            } else if (stat.equals("winrate")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    if (statList.get(ctr).get("bjrounds") == 0){
                        test.add(0.);
                    } else {
                        test.add((double) statList.get(ctr).get("bjwinnings") / (double) statList.get(ctr).get("bjrounds"));
                    }
                }
                line += "Blackjack Win Rate: ";
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
                    test.add((double) statList.get(ctr).get("bjwinnings"));
                }
                title += " Blackjack Winnings ";
            } else if (stat.equals("rounds")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("bjrounds"));
                }
                title += " Blackjack Rounds ";
            } else if (stat.equals("winrate")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    if (statList.get(ctr).get("bjrounds") == 0){
                        test.add(0.);
                    } else {
                        test.add((double) statList.get(ctr).get("bjwinnings") / (double) statList.get(ctr).get("bjrounds"));
                    }
                }
                title += " Blackjack Win Rate ";
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
    
    /* Formatted strings */   
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
        return String.format(getMsg("bj_stats"), getTotalPlayers(), getGameNameStr(), getTotalRounds(), getTotalHouse());
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