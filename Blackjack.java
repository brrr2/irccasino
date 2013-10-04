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

public class Blackjack extends CardGame {
	/* Nested class to create a shuffle timer thread */
	public class IdleShuffleTask extends TimerTask {
		private Blackjack game;
		public IdleShuffleTask(Blackjack g) {
			game = g;
		}

		@Override
		public void run() {
			game.shuffleShoe();
		}
	}
	/* Nested class to store statistics, based on number of decks used, for the house */
	private class HouseStat extends Stats {
		public HouseStat() {
			this(0, 0, 0);
		}
        
		public HouseStat(int a, int b, int c) {
            statsMap = new HashMap<String,Integer>();
            statsMap.put("decks", a);
            statsMap.put("rounds", b);
            statsMap.put("cash", c);
		}
        
        @Override
		public String toString() {
			return formatNumber(get("rounds")) + " round(s) have been played using " 
					+ formatNumber(get("decks")) + " deck shoes. The house has won $"
					+ formatNumber(get("cash")) + " during those round(s).";
		}
	}
	
	private BlackjackPlayer dealer;
	private boolean insuranceBets, countEnabled, holeEnabled, soft17hit;
	private int shufflePoint, shoeDecks, idleShuffleTime;
	private ArrayList<HouseStat> houseStatsList;
    private IdleShuffleTask idleShuffleTask;
	private HouseStat house;

	/**
	 * Class constructor for Blackjack, a subclass of CardGame.
	 * 
	 * @param parent The bot that created an instance of this ListenerAdapter
	 * @param gameChannel The IRC channel in which the game is to be run.
     * @param eb The ListenerAdapter that is listening for commands for this game
	 */
	public Blackjack(PircBotX parent, Channel gameChannel, ExampleBot eb) {
		super(parent, gameChannel, eb);
		setGameName("Blackjack");
        setIniFile("blackjack.ini");
        setHelpFile("blackjack.help");
		dealer = new BlackjackPlayer(bot.getNick(),"",true);
		houseStatsList = new ArrayList<HouseStat>();
		loadHouseStats();
		loadSettings();
		insuranceBets = false;
        idleShuffleTask = null;
	}

    @Override
    public void processCommand(User user, String command, String[] params){
        String nick = user.getNick();
        String hostmask = user.getHostmask();

        /* Check if it's a common command */
        super.processCommand(user, command, params);
        
        /* Parsing commands from the channel */
        if (command.equals("join") || command.equals("j")){
            if (parentListener.tpgame != null && 
                (parentListener.tpgame.isJoined(nick) || parentListener.tpgame.isWaitlisted(nick))){
                bot.sendNotice(user, "You're already joined in "+parentListener.tpgame.getGameNameStr()+"!");
            } else if (parentListener.tpgame != null && parentListener.tpgame.isBlacklisted(nick)){
                infoBlacklisted(nick);
            } else {
                join(nick, hostmask);
            }
        } else if (command.equals("start") || command.equals("go")){
            if (isStartAllowed(nick)) {
                cancelIdleShuffleTask();
                setInProgress(true);
                showStartRound();
                setStartRoundTask();
            }
        } else if (command.equals("bet") || command.equals("b")) {
            if (isStage1PlayerTurn(nick)){
                if (params.length > 0){
                    try {
                        bet(Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
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
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
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
                infoPlayerSum(p, p.getHand());
            }
        } else if (command.equals("hand")) {
            if (isStage2(nick)){
                BlackjackPlayer p = (BlackjackPlayer) findJoined(nick);
                infoPlayerHand(p, p.getHand());
            }
        } else if (command.equals("allhands")) {
            bot.sendNotice(nick, "This command is not implemented.");
        } else if (command.equals("turn")) {
            if (!isJoined(nick)) {
                infoNotJoined(nick);
            } else if (!isInProgress()) {
                infoNotStarted(nick);
            } else {
                BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
                if (p.hasSplit()){
                    showTurn(p, p.getCurrentIndex()+1);
                } else {
                    showTurn(p);
                }
            }
            /* Contributed by Yky */
        } else if (command.equals("zc") || (command.equals("zen"))) {
            if (isCountAllowed(nick)){
                bot.sendMessage(channel, "Zen count = " + getZen());
            }
            /* Contributed by Yky */
        } else if (command.equals("hc") || (command.equals("hilo"))) {
            if (isCountAllowed(nick)){
                bot.sendMessage(channel, "Hi-Lo count = " + getHiLo());
            }
            /* Contributed by Yky */
        } else if (command.equals("rc") || (command.equals("red7"))) {
            if (isCountAllowed(nick)){
                bot.sendMessage(channel, "Red7 count = " + getRed7());
            }
        } else if (command.equals("count") || command.equals("c")){
            if (isCountAllowed(nick)){
                bot.sendMessage(channel, "Cards/Hi-Lo/Red7/Zen = " + 
                        formatNumber(deck.getNumberCards()) + "/" +
                        getHiLo() + "/" + getRed7() + "/" + getZen());
            }
        } else if (command.equals("numcards") || command.equals("ncards")) {
            if (!isJoined(nick)) {
                infoNotJoined(nick);
            } else if (isInProgress()) {
                infoWaitRoundEnd(nick);
            } else {
                showNumCards();
            }
        } else if (command.equals("numdiscards") || command.equals("ndiscards")) {
            if (!isJoined(nick)) {
                infoNotJoined(nick);
            } else if (isInProgress()) {
                infoWaitRoundEnd(nick);
            } else {
                showNumDiscards();
            }
        } else if (command.equals("numdecks") || command.equals("ndecks")) {
            showNumDecks();
        } else if (command.equals("players")) {
            showPlayers();
        } else if (command.equals("house")) {
            if (isInProgress()) {
                infoWaitRoundEnd(nick);
            } else {
                if (params.length > 0){
                    try {
                        showHouseStat(Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    showHouseStat(shoeDecks);
                }
            }
        /* Op commands */
        } else if (command.equals("fstart") || command.equals("fgo")){
            if (isForceStartAllowed(user,nick)){
                cancelIdleShuffleTask();
                setInProgress(true);
                showStartRound();
                setStartRoundTask();
            }
        } else if (command.equals("fstop")){
            // Use only as last resort. Data will be lost.
            if (isForceStopAllowed(user,nick)){
                BlackjackPlayer p;
                cancelIdleOutTask();
                for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
                    p = (BlackjackPlayer) getJoined(ctr);
                    resetPlayer(p);
                }
                resetGame();
                showEndRound();
                setInProgress(false);
            }
        } else if (command.equals("fj") || command.equals("fjoin")){
            if (!channel.isOp(user)) {
                infoOpsOnly(nick);
            } else {
                if (params.length > 0){
                    String fNick = params[0];
                    Set<User> chanUsers = channel.getUsers();
                    Iterator<User> it = chanUsers.iterator();
                    while(it.hasNext()){
                        User u = it.next();
                        if (u.getNick().equalsIgnoreCase(fNick)){
                            // Check if fNick is joined in another game
                            if (parentListener.tpgame != null && 
                                (parentListener.tpgame.isJoined(fNick) || parentListener.tpgame.isWaitlisted(fNick))){
                                bot.sendNotice(user, u.getNick()+" is already joined in "+parentListener.tpgame.getGameNameStr()+"!");
                            } else if (parentListener.tpgame != null && parentListener.tpgame.isBlacklisted(fNick)){
                                bot.sendNotice(user, u.getNick()+" is bankrupt and cannot join!");
                            } else {
                                join(u.getNick(), u.getHostmask());
                            }
                            return;
                        }
                    }
                    infoNickNotFound(nick,fNick);
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("fb") || command.equals("fbet")){
            if (isForceBetAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        bet(Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
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
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
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
                loadSettings();
                showReloadSettings();
            }
        } else if (command.equals("test1")){
            bot.sendMessage(channel, "No test implemented yet.");
        }
    }

	/* Accessor methods */
	public void setShoeDecks(int value) {
		shoeDecks = value;
	}
	public int getShoeDecks() {
		return shoeDecks;
	}
	public void setCountEnabled(boolean value) {
		countEnabled = value;
	}
	public boolean isCountEnabled() {
		return countEnabled;
	}
	public void setHoleEnabled(boolean value){
		holeEnabled = value;
	}
	public boolean isHoleEnabled(){
		return holeEnabled;
	}
	public void setIdleShuffleTime(int value) {
		idleShuffleTime = value;
	}
	public int getIdleShuffleTime() {
		return idleShuffleTime;
	}
    public void setShufflePoint(int value){
        shufflePoint = value;
    }
    public int getShufflePoint(){
        return shufflePoint;
    }
    public void setSoft17Hit(boolean value){
        soft17hit = value;
    }
    public boolean isSoft17Hit(){
        return soft17hit;
    }
    public boolean hasInsuranceBets() {
		return insuranceBets;
	}
	public void setInsuranceBets(boolean b) {
		insuranceBets = b;
	}

	/* Game settings management */
	@Override
	protected void setSetting(String[] params) {
		String setting = params[0].toLowerCase();
		String value = params[1];
		if (setting.equals("decks")) {
			cancelIdleShuffleTask();
			setShoeDecks(Integer.parseInt(value));
			deck = new CardDeck(getShoeDecks());
			deck.shuffleCards();
			house = getHouseStat(getShoeDecks());
			if (house == null) {
				house = new HouseStat(getShoeDecks(), 0, 0);
				houseStatsList.add(house);
			}
		} else if (setting.equals("idle")) {
			setIdleOutTime(Integer.parseInt(value));
        } else if (setting.equals("idlewarning")) {
            setIdleWarningTime(Integer.parseInt(value));
		} else if (setting.equals("idleshuffle")) {
			setIdleShuffleTime(Integer.parseInt(value));
		} else if (setting.equals("cash")) {
			setNewCash(Integer.parseInt(value));
		} else if (setting.equals("respawn")) {
			setRespawnTime(Integer.parseInt(value));
		} else if (setting.equals("count")) {
			setCountEnabled(Boolean.parseBoolean(value));
		} else if (setting.equals("hole")) {
			setHoleEnabled(Boolean.parseBoolean(value));
		} else if (setting.equals("minbet")) {
			setMinBet(Integer.parseInt(value));
		} else if (setting.equals("shufflepoint")) {
			setShufflePoint(Integer.parseInt(value));
		} else if (setting.equals("maxplayers")) {
			setMaxPlayers(Integer.parseInt(value));
        } else if (setting.equals("soft17hit")) {
            setSoft17Hit(Boolean.parseBoolean(value));
		} else {
			throw new IllegalArgumentException();
		}
		saveSettings();
	}
	@Override
	protected String getSetting(String param){
		if (param.equals("decks")) {
			return getShoeDecks()+"";
		} else if (param.equals("idle")) {
			return getIdleOutTime()+"";
		} else if (param.equals("idlewarning")) {
            return getIdleWarningTime()+"";
		} else if (param.equals("idleshuffle")) {
			return getIdleShuffleTime()+"";
		} else if (param.equals("cash")) {
			return getNewCash()+"";
		} else if (param.equals("respawn")) {
			return getRespawnTime()+"";
		} else if (param.equals("count")) {
			return isCountEnabled()+"";
		} else if (param.equals("hole")) {
			return isHoleEnabled()+"";
		} else if (param.equals("minbet")) {
			return getMinBet()+"";
		} else if (param.equals("shufflepoint")) {
			return getShufflePoint()+"";
		} else if (param.equals("maxplayers")) {
			return getMaxPlayers()+"";
        } else if (param.equals("soft17hit")) {
            return isSoft17Hit()+"";
		} else {
			throw new IllegalArgumentException();
		}
	}
	@Override
	protected final void loadSettings() {
		try {
			BufferedReader in = new BufferedReader(new FileReader(getIniFile()));
			String str, name, value;
			StringTokenizer st;
			while (in.ready()) {
				str = in.readLine();
				if (str.startsWith("#")) {
					continue;
				}
				st = new StringTokenizer(str, "=");
				name = st.nextToken();
				value = st.nextToken();
				if (name.equals("decks")) {
					setShoeDecks(Integer.parseInt(value));
				} else if (name.equals("idle")) {
					setIdleOutTime(Integer.parseInt(value));
                } else if (name.equals("idlewarning")) {
                    setIdleWarningTime(Integer.parseInt(value));
				} else if (name.equals("idleshuffle")) {
					setIdleShuffleTime(Integer.parseInt(value));
				} else if (name.equals("cash")) {
					setNewCash(Integer.parseInt(value));
				} else if (name.equals("respawn")) {
					setRespawnTime(Integer.parseInt(value));
				} else if (name.equals("count")) {
					setCountEnabled(Boolean.parseBoolean(value));
				} else if (name.equals("hole")){
					setHoleEnabled(Boolean.parseBoolean(value));
				} else if (name.equals("minbet")){
                    setMinBet(Integer.parseInt(value));
                } else if (name.equals("shufflepoint")){
                    setShufflePoint(Integer.parseInt(value));
                } else if (name.equals("maxplayers")){
                    setMaxPlayers(Integer.parseInt(value));
                } else if (name.equals("soft17hit")){
                    setSoft17Hit(Boolean.parseBoolean(value));
                }
			}
			in.close();
		} catch (IOException e) {
			/* load defaults if blackjack.ini is not found */
			bot.log(getIniFile()+" not found! Creating new "+getIniFile()+"...");
			setShoeDecks(8);
			setNewCash(1000);
			setIdleOutTime(60);
            setIdleWarningTime(45);
			setRespawnTime(600);
			setIdleShuffleTime(300);
			setCountEnabled(true);
			setHoleEnabled(false);
            setMaxPlayers(15);
            setMinBet(5);
            setShufflePoint(10);
            setSoft17Hit(false);
			saveSettings();
		}
		deck = new CardDeck(getShoeDecks());
		deck.shuffleCards();
		house = getHouseStat(getShoeDecks());
		if (house == null) {
			house = new HouseStat(getShoeDecks(), 0, 0);
			houseStatsList.add(house);
		}
	}
	@Override
	protected void saveSettings() {
		try {
			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(getIniFile())));
			out.println("#Settings");
			out.println("#Number of decks in the dealer's shoe");
			out.println("decks=" + getShoeDecks());
			out.println("#Number of seconds before a player idles out");
			out.println("idle=" + getIdleOutTime());
            out.println("#Number of seconds before a player is given a warning for idling");
			out.println("idlewarning=" + getIdleWarningTime());
			out.println("#Number of seconds of idleness after a round ends before the deck is shuffled");
			out.println("idleshuffle=" + getIdleShuffleTime());
			out.println("#Initial amount given to new and bankrupt players");
			out.println("cash=" + getNewCash());
			out.println("#Number of seconds before a bankrupt player is allowed to join again");
			out.println("respawn=" + getRespawnTime());
			out.println("#Whether card counting functions are enabled");
			out.println("count=" + isCountEnabled());
			out.println("#Whether player hands are shown with a hole card in the main channel");
			out.println("hole=" + isHoleEnabled());
            out.println("#The minimum bet required to see a hand");
			out.println("minbet=" + getMinBet());
            out.println("#The number of cards remaining in the shoe when the discards are shuffled back");
			out.println("shufflepoint=" + getShufflePoint());
			out.println("#The maximum number of players allowed to join the game");
			out.println("maxplayers=" + getMaxPlayers());
            out.println("#Whether or not the dealer hits on soft 17");
			out.println("soft17hit=" + isSoft17Hit());
            out.close();
		} catch (IOException e) {
			bot.log("Error creating "+getIniFile()+"!");
		}
	}

	/* House stats management */
	public final void loadHouseStats() {
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
			bot.log("housestats.txt not found! Creating new housestats.txt...");
			try {
				PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("housestats.txt")));
				out.close();
			} catch (IOException f) {
				bot.log("Error creating housestats.txt!");
			}
		}
	}
	public void saveHouseStats() {
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
			bot.log("Error reading housestats.txt!");
		}
		if (!found) {
			lines.add("#blackjack");
			index = lines.size();
		}
		for (int ctr = 0; ctr < houseStatsList.size(); ctr++) {
			lines.add(index, houseStatsList.get(ctr).get("decks") + " "
					+ houseStatsList.get(ctr).get("rounds") + " "
					+ houseStatsList.get(ctr).get("cash"));
		}
		try {
			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("housestats.txt")));
			for (int ctr = 0; ctr < lines.size(); ctr++) {
				out.println(lines.get(ctr));
			}
			out.close();
		} catch (IOException e) {
			bot.log("Error writing to housestats.txt!");
		}
	}
	public HouseStat getHouseStat(int numDecks) {
		HouseStat hs;
		for (int ctr = 0; ctr < houseStatsList.size(); ctr++) {
			hs = houseStatsList.get(ctr);
			if (hs.get("decks") == numDecks) {
				return hs;
			}
		}
		return null;
	}
	public int getTotalRounds(){
		int total=0;
		for (int ctr=0; ctr < houseStatsList.size(); ctr++){
			total += houseStatsList.get(ctr).get("rounds");
		}
		return total;
	}
	public int getTotalHouse(){
		int total=0;
		for (int ctr=0; ctr < houseStatsList.size(); ctr++){
			total += houseStatsList.get(ctr).get("cash");
		}
		return total;
	}
	
	/* Game management methods */
    @Override
    public void addPlayer(String nick, String hostmask) {
		addPlayer(new BlackjackPlayer(nick, hostmask, false));
	}
	@Override
	public void addWaitlistPlayer(String nick, String hostmask) {
		Player p = new BlackjackPlayer(nick, hostmask, false);
		waitlist.add(p);
		infoJoinWaitlist(p.getNick());
	}
	@Override
	public void leave(String nick) {
        // Check if the nick is even joined
		if (isJoined(nick)){
			BlackjackPlayer p = (BlackjackPlayer) findJoined(nick);
            // Check if a round is in progress
			if (isInProgress()) {
                // If in the betting or post-start wait phase
				if (isBetting() || currentPlayer == null){
					if (p == currentPlayer){
						currentPlayer = getNextPlayer();
						removeJoined(p);
						if (currentPlayer == null) {
							setBetting(false);
							if (getNumberJoined() == 0) {
								endRound();
							} else {
								dealTable();
								currentPlayer = getJoined(0);
								quickEval();
							}
						} else {
							showTurn(currentPlayer);
                            setIdleOutTask();
						}
					} else {
						removeJoined(p);
					}
                // If in the card-playing phase
				} else {
                    bot.sendNotice(p.getNick(), "You will be removed at the end of the round.");
					p.setQuit(true);
					if (p == currentPlayer){
						stay();
					}
				}
			} else {
				removeJoined(p);
			}
        // Check if on the waitlist
		} else if (isWaitlisted(nick)) {
			infoLeaveWaitlist(nick);
			removeWaitlisted(nick);
		} else {
			infoNotJoined(nick);
		}
	}
	@Override
	public void startRound() {
		if (getNumberJoined() > 0) {
            showPlayers();
			setBetting(true);
			currentPlayer = getJoined(0);
			showTurn(currentPlayer);
			setIdleOutTask();
		} else {
			endRound();
		}
	}
	@Override
	public void continueRound(){
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		BlackjackHand nextHand;
		if (p.getCurrentIndex() < p.getNumberHands() - 1) {
			nextHand = p.getNextHand();
			showPlayerHand(p, nextHand, p.getCurrentIndex() + 1);
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
		BlackjackPlayer p;
		BlackjackHand dHand;
		if (getNumberJoined() >= 1) {
			house.increment("rounds");
			// Make dealer decisions
			showTurn(dealer);
			dHand = dealer.getHand();
			showPlayerHand(dealer, dHand, true);
			if (needDealerHit()) {
				while (dHand.calcSum() < 17 || (dHand.isSoft17() && isSoft17Hit())) {
					dealCard(dHand);
					showPlayerHand(dealer, dHand, true);
				}
			}
			if (dHand.isBlackjack()) {
				showBlackjack(dealer, 0);
			} else if (dHand.isBusted()) {
				showBusted(dealer, 0);
			}
			// Show results
			showResults();
			if (hasInsuranceBets()) {
				showInsuranceResults();
			}
			/* Clean-up tasks
             * 1. Increment the number of rounds played for player
             * 2. Remove players who have gone bankrupt and set respawn timers
             * 3. Remove players who have quit mid-round
             * 4. Save player data
             * 5. Reset the player
             */
			for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
				p = (BlackjackPlayer) getJoined(ctr);
				p.increment("bjrounds");
                
                // Bankrupts
				if (!p.has("cash")) {
                    // Make a withdrawal if the player has a positive bankroll
                    if (p.get("bank") > 0){
                        int amount = Math.min(p.get("bank"), getNewCash());
                        p.bankTransfer(-amount);
                        savePlayerData(p);
                        infoAutoWithdraw(p.getNick(),amount);
                        // Check if the player has quit
                        if (p.hasQuit()){
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
				} else if (p.hasQuit()) {
					removeJoined(p.getNick());
					ctr--;
                // Remaining players
				} else {
                    savePlayerData(p);
                }
				resetPlayer(p);
			}
            saveHouseStats();
		} else {
			showNoPlayers();
		}
		resetGame();
		showEndRound();
        setInProgress(false);
		mergeWaitlist();
		if (deck.getNumberDiscards() > 0) {
			setIdleShuffleTask();
		}
	}
	@Override
	public void endGame() {
		cancelStartRoundTask();
		cancelIdleOutTask();
		cancelIdleShuffleTask();
		cancelRespawnTasks();
        gameTimer.cancel();
		devoiceAll();
		joined.clear();
		waitlist.clear();
		blacklist.clear();
		deck = null;
		dealer = null;
		currentPlayer = null;
        showGameEnd();
        bot = null;
        channel = null;
        parentListener = null;
	}
	@Override
	public void resetGame() {
		discardPlayerHand(dealer);
		setInsuranceBets(false);
		currentPlayer = null;
	}
	/**
     * Resets a BlackjackPlayer back to default values.
     * This method is called at the end of a Blackjack round for each player in
     * preparation for the following round.
     * 
     * @param p the player
     */
    public void resetPlayer(BlackjackPlayer p) {
		discardPlayerHand(p);
		p.resetCurrentIndex();
		p.clear("initialbet");
		p.setQuit(false);
		p.setSurrender(false);
		p.clear("insurebet");
	}
	public void setIdleShuffleTask() {
        idleShuffleTask = new IdleShuffleTask(this);
		gameTimer.schedule(idleShuffleTask, idleShuffleTime*1000);
	}
	public void cancelIdleShuffleTask() {
        if (idleShuffleTask != null){
            idleShuffleTask.cancel();
            gameTimer.purge();
        }
	}
    
    /* Game command logic checking methods */
    private boolean isStartAllowed(String nick){
        if (!isJoined(nick)) {
            infoNotJoined(nick);
        } else if (isInProgress()) {
            infoRoundStarted(nick);
        } else if (getNumberJoined() < 1) {
            showNoPlayers();
        } else {
            return true;
        }
        return false;
    }
	private boolean isStage1PlayerTurn(String nick){
		if (!isJoined(nick)) {
			infoNotJoined(nick);
		} else if (!isInProgress()) {
			infoNotStarted(nick);
		} else if (!isBetting()) {
			infoNotBetting(nick);
		} else if (currentPlayer != findJoined(nick)) {
			infoNotTurn(nick);
		} else {
            return true;
        }
        return false;
	}
	private boolean isStage2(String nick){
		if (!isJoined(nick)) {
			infoNotJoined(nick);
		} else if (!isInProgress()) {
			infoNotStarted(nick);
		} else if (isBetting()) {
			infoNoCards(nick);
		} else {
            return true;
        }
        return false;
	}
	private boolean isStage2PlayerTurn(String nick){
		if (!isStage2(nick)){
		} else if (!(currentPlayer == findJoined(nick))) {
			infoNotTurn(nick);
		} else {
            return true;
        }
        return false;
	}
    private boolean isForceStartAllowed(User user, String nick){
        if (!channel.isOp(user)) {
			infoOpsOnly(nick);
		} else if (isInProgress()) {
            infoRoundStarted(nick);
        } else if (getNumberJoined() < 1) {
            showNoPlayers();
        } else {
            return true;
        }
        return false;
    }
    private boolean isForceStopAllowed(User user, String nick){
        if (!channel.isOp(user)){
            infoOpsOnly(nick);
        } else if (!isInProgress()){
            infoNotStarted(nick);
        } else {
            return true;
        }
        return false;
    }
    private boolean isForcePlayAllowed(User user, String nick){
		if (!channel.isOp(user)) {
			infoOpsOnly(nick);
		} else if (!isInProgress()) {
			infoNotStarted(nick);
		} else if (isBetting()) {
            infoNoCards(nick);
        } else {
            return true;
        }
        return false;
	}
    private boolean isForceBetAllowed(User user, String nick){
		if (!channel.isOp(user)) {
			infoOpsOnly(nick);
		} else if (!isInProgress()) {
			infoNotStarted(nick);
		} else if (!isBetting()) {
            infoNotBetting(nick);
        } else {
            return true;
        }
        return false;
	}
	private boolean isCountAllowed(String nick){
		if (!isJoined(nick)) {
			infoNotJoined(nick);
		} else if (isInProgress()) {
			infoWaitRoundEnd(nick);
		} else if (!isCountEnabled()) {
			infoCountDisabled(nick);
		} else {
            return true;
        }
        return false;
	}

	/* Card management methods for Blackjack */
    @Override
    public void dealCard(Hand h) {
		h.add(deck.takeCard());
		if (deck.getNumberCards() == shufflePoint) {
			showDeckEmpty();
			deck.refillDeck();
		}
	}
	/**
     * Merges the discards and shuffles the shoe.
     */
    public void shuffleShoe() {
		deck.refillDeck();
		showShuffleShoe();
	}
	/**
     * Deals two cards to the specified player.
     * @param p the player to be dealt to
     */
    public void dealHand(BlackjackPlayer p) {
        p.addHand();
        dealCard(p.getHand());
        dealCard(p.getHand());
	}
	/**
     * Deals hands to everybody at the table.
     */
    public void dealTable() {
		BlackjackPlayer p;
		BlackjackHand h;
		for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
			p = (BlackjackPlayer) getJoined(ctr);
			dealHand(p);
			h = p.getHand();
			h.setBet(p.get("initialbet"));
			if (isHoleEnabled()) {
				infoPlayerHand(p, h);
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
    public void discardPlayerHand(BlackjackPlayer p) {
		if (p.hasHands()) {
			for (int ctr = 0; ctr < p.getNumberHands(); ctr++) {
				deck.addToDiscard(p.getHand(ctr).getAllCards());
			}
			p.resetHands();
		}
	}

	/* Blackjack gameplay methods */
    
    /**
     * Sets the initial bet for the current player to see a hand.
     * The game then moves on to the next hand, player or phase.
     * 
     * @param amount the bet on the hand
     */
	private void bet(int amount) {
		cancelIdleOutTask();    
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        // Check if amount is greater than the player's stack
        if (amount > p.get("cash")) {
			infoBetTooHigh(p.getNick(), p.get("cash"));
			setIdleOutTask();
        // Check if the amount is less than minimum bet
		} else if (amount < getMinBet() && amount < p.get("cash")) {
			infoBetTooLow(p.getNick());
			setIdleOutTask();
		} else {
			p.set("initialbet", amount);
			p.add("cash", -1 * amount);
            p.add("bjwinnings", -1 * amount);
			house.add("cash", amount);
			showProperBet(p);
			currentPlayer = getNextPlayer();
			if (currentPlayer == null) {
				setBetting(false);
				dealTable();
				currentPlayer = getJoined(0);
				quickEval();
			} else {
				showTurn(currentPlayer);
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
		if (h.isBusted()) {
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
			infoNotDoubleDown(p.getNick());
			setIdleOutTask();
		} else if (p.get("initialbet") > p.get("cash")) {
			infoInsufficientFunds(p.getNick());
			setIdleOutTask();
		} else {			
			p.add("cash", -1 * h.getBet());
            p.add("bjwinnings", -1 * h.getBet());
			house.add("cash", h.getBet());
			h.addBet(h.getBet());
			showDoubleDown(p, h);
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
			infoNotSurrenderSplit(p.getNick());
			setIdleOutTask();
		} else if (h.hasHit()) {
			infoNotSurrender(p.getNick());
			setIdleOutTask();
		} else {
			p.add("cash", calcHalf(p.get("initialbet")));
            p.add("bjwinnings", calcHalf(p.get("initialbet")));
			house.add("cash", -1 * calcHalf(p.get("initialbet")));
			p.setSurrender(true);
			showSurrender(p);
			continueRound();
		}
	}
    
    /**
     * Sets the insurance bet for the current Player's hand.
     * 
     * @param amount the insurance bet
     */
	private void insure(int amount) {
		cancelIdleOutTask();
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		BlackjackHand h = p.getHand();
		if (p.has("insurebet")) {
			infoAlreadyInsured(p.getNick());
		} else if (!dealerUpcardAce()) {
			infoNotInsureNoAce(p.getNick());
		} else if (h.hasHit()) {
			infoNotInsureHasHit(p.getNick());
		} else if (p.hasSplit()){
			infoNotInsureHasSplit(p.getNick());
		} else if (amount > p.get("cash")) {
			infoInsufficientFunds(p.getNick());
		} else if (amount > calcHalf(p.get("initialbet"))) {
			infoInsureBetTooHigh(p.getNick(), calcHalf(p.get("initialbet")));
		} else if (amount <= 0) {
			infoInsureBetTooLow(p.getNick());
		} else {
			setInsuranceBets(true);
			p.set("insurebet", amount);
			p.add("cash", -1 * amount);
            p.add("bjwinnings", -1 * amount);
			house.add("cash", amount);
			showInsure(p);
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
			infoNotPair(p.getNick());
			setIdleOutTask();
		} else if (p.get("cash") < cHand.getBet()) {
			infoInsufficientFunds(p.getNick());
			setIdleOutTask();
		} else {
			p.add("cash", -1 * cHand.getBet());
            p.add("bjwinnings", -1 * cHand.getBet());
			house.add("cash", cHand.getBet());
			p.splitHand();
			dealCard(cHand);
			nHand = p.getHand(p.getCurrentIndex() + 1);
			dealCard(nHand);
			nHand.setBet(cHand.getBet());
			showSplitHands(p);
			showSeparator();
			showPlayerHand(p, cHand, p.getCurrentIndex() + 1);
			quickEval();
		}
	}

	/* Blackjack behind-the-scenes methods */
	private void quickEval() {
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		BlackjackHand h = p.getHand();
		if (p.hasSplit()) {
			showTurn(p, p.getCurrentIndex() + 1);
		} else {
			showTurn(p);
		}
		if (h.isBlackjack()) {
			if (p.hasSplit()){
				showBlackjack(p, p.getCurrentIndex() + 1);
			} else {
				showBlackjack(p, 0);
			}
		}
		if (p.hasQuit()){
			stay();
		} else {
			setIdleOutTask();
		}
	}
	private int calcHalf(int amount) {
		return (int) (Math.ceil((double) (amount) / 2.));
	}
	private int calcBlackjackPayout(BlackjackHand h){
		return (2 * h.getBet() + calcHalf(h.getBet()));
	}
	private int calcWinPayout(BlackjackHand h){
		return 2 * h.getBet();
	}
	private int calcInsurancePayout(BlackjackPlayer p){
		return 3 * p.get("insurebet");
	}
	private boolean dealerUpcardAce() {
		return dealer.getHand().get(1).getFace().equals("A");
	}
	private boolean needDealerHit() {
		for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
			BlackjackPlayer p = (BlackjackPlayer) getJoined(ctr);
			for (int ctr2 = 0; ctr2 < p.getNumberHands(); ctr2++) {
				BlackjackHand h = p.getHand(ctr2);
				if (!h.isBusted() && !p.hasSurrendered() && !h.isBlackjack()) {
					return true;
				}
			}
		}
		return false;
	}
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
	private void payPlayerInsurance(BlackjackPlayer p){
		if (dealer.getHand().isBlackjack()) {
			p.add("cash", calcInsurancePayout(p));
            p.add("bjwinnings", calcInsurancePayout(p));
			house.add("cash", -1 * calcInsurancePayout(p));
		}
	}

    /* Card-counting methods */
	/* contributed by Yky */
	private int getZen() {
		int zenCount = 0;
		String face;
		ArrayList<Card> discards = deck.getDiscards();
		for (int i = 0; i < deck.getNumberDiscards(); i++) {
			face = discards.get(i).getFace();
			if (face.equals("2") || face.equals("3")) {
				zenCount++;
			} else if (face.equals("4") || face.equals("5") || face.equals("6")) {
				zenCount += 2;
			} else if (face.equals("7")) {
				zenCount += 1;
			} else if (face.equals("T") || face.equals("J") || face.equals("Q")
					|| face.equals("K")) {
				zenCount -= 2;
			} else if (face.equals("A")) {
				zenCount -= 1;
			}
		}
		return zenCount;
	}
	/* contributed by Yky */
	private int getHiLo() {
		int hiLo = 0;
		String face;
		ArrayList<Card> discards = deck.getDiscards();
		for (int i = 0; i < deck.getNumberDiscards(); i++) {
			face = discards.get(i).getFace();
			if (face.equals("2") || face.equals("3") || face.equals("4")
					|| face.equals("5") || face.equals("6")) {
				hiLo++;
			} else if (face.equals("T") || face.equals("J") || face.equals("Q")
					|| face.equals("K") || face.equals("A")) {
				hiLo--;
			}
		}
		return hiLo;
	}
	/* contributed by Yky */
	private double getRed7() {
		double red7 = -2 * getShoeDecks();
		String face;
		ArrayList<Card> discards = deck.getDiscards();
		for (int i = 0; i < deck.getNumberDiscards(); i++) {
			face = discards.get(i).getFace();
			if (face.equals("2") || face.equals("3") || face.equals("4")
					|| face.equals("5") || face.equals("6")) {
				red7++;
			} else if (face.equals("T") || face.equals("J") || face.equals("Q")
					|| face.equals("K") || face.equals("A")) {
				red7--;
			} else if (face.equals("7")) {
				red7 += 0.5;
			}
		}
		return red7;
	}
    
	/* Channel message output methods for Blackjack */
	@Override
	public void showGameStats(){
		int totalPlayers, totalRounds, totalHouse;
		totalPlayers = getTotalPlayers();
		totalRounds = getTotalRounds();
		totalHouse = getTotalHouse();
		bot.sendMessage(channel, formatNumber(totalPlayers)+" player(s) have played " +
					getGameNameStr()+". They have played a total of " +
					formatNumber(totalRounds) + " rounds. The house has won $" +
					formatNumber(totalHouse) + " in those rounds.");
	}
	@Override
	public void showTurn(Player p) {
		if (isBetting()) {
			bot.sendMessage(channel, p.getNickStr() + "'s turn. Stack: $"
							+ formatNumber(p.get("cash"))
							+ ". Enter an initial bet up to $"
							+ formatNumber(p.get("cash")) + ".");
		} else {
			bot.sendMessage(channel, "It's now " + p.getNickStr() + "'s turn.");
		}
	}
	public void showTurn(Player p, int index) {
		String nickStr = p.getNickStr() + "-" + index;
		bot.sendMessage(channel, "It's now " + nickStr + "'s turn.");
	}
	public void showPlayerHand(BlackjackPlayer p, BlackjackHand h, boolean forceNoHole) {
		if (forceNoHole){
			bot.sendMessage(channel, p.getNickStr() + ": " + h.toString());
		} else if (isHoleEnabled() || p.isDealer()) {
			bot.sendMessage(channel, p.getNickStr() + ": " + h.toString(1));
		} else {
			bot.sendMessage(channel, p.getNickStr() + ": " + h.toString());
		}
	}
	public void showPlayerHand(BlackjackPlayer p, BlackjackHand h, int handIndex) {
		if (isHoleEnabled() || p.isDealer()) {
			bot.sendMessage(channel, p.getNickStr() + "-" + handIndex + ": "
					+ h.toString(1));
		} else {
			bot.sendMessage(channel, p.getNickStr() + "-" + handIndex + ": "
					+ h.toString());
		}
	}
	public void showPlayerHandWithBet(BlackjackPlayer p, BlackjackHand h, int handIndex) {
		if (isHoleEnabled() || p.isDealer()) {
			bot.sendMessage(channel,
					p.getNickStr() + "-" + handIndex + ": " + h.toString(1)
							+ ", bet: $" + formatNumber(h.getBet()));
		} else {
			bot.sendMessage(channel,
					p.getNickStr() + "-" + handIndex + ": " + h.toString()
							+ ", bet: $" + formatNumber(h.getBet()));
		}
	}
    @Override
	public void showDeckEmpty() {
		bot.sendMessage(channel, "Merging the discards back into the shoe and shuffling...");
	}
	public void showProperBet(BlackjackPlayer p) {
		bot.sendMessage(channel, p.getNickStr() + " bets $"
						+ formatNumber(p.get("initialbet"))
						+ ". Stack: $" + formatNumber(p.get("cash")));
	}
	public void showDoubleDown(BlackjackPlayer p, BlackjackHand h) {
		bot.sendMessage(channel, p.getNickStr() +
                " has doubled down! The bet is now $" + formatNumber(h.getBet()) + 
                ". Stack: $"+formatNumber(p.get("cash")));
	}
	public void showSurrender(BlackjackPlayer p) {
		bot.sendMessage(channel, p.getNickStr()
						+ " has surrendered! Half the bet is returned. Stack: $"
						+ formatNumber(p.get("cash")));
	}
	public void showInsure(BlackjackPlayer p) {
		bot.sendMessage(channel,
				p.getNickStr() + " has made an insurance bet of $"
						+ formatNumber(p.get("insurebet")) + ". Stack: $"
						+ formatNumber(p.get("cash")));
	}
	public void showSplitHands(BlackjackPlayer p) {
		BlackjackHand h;
		bot.sendMessage(channel,
				p.getNickStr() + " has split the hand! " + p.getNickStr()
						+ "'s hands are now:");
		for (int ctr = 0; ctr < p.getNumberHands(); ctr++) {
			h = p.getHand(ctr);
			showPlayerHandWithBet(p, h, ctr + 1);
		}
		bot.sendMessage(channel, p.getNickStr() + "'s stack: $" + formatNumber(p.get("cash")));
	}
	public void showShuffleShoe() {
		bot.sendMessage(channel, "The dealer's shoe has been shuffled.");
	}
	public void showHouseStat(int n) {
		HouseStat hs = getHouseStat(n);
		if (hs != null) {
			bot.sendMessage(channel, hs.toString());
		} else {
			bot.sendMessage(channel, "No statistics found for " + n	+ " deck(s).");
		}
	}
	@Override
	public void showNumCards() {
		bot.sendMessage(channel, formatNumber(deck.getNumberCards())
				+ " cards left in the dealer's shoe.");
	}
	public void showHitResult(BlackjackPlayer p, BlackjackHand h){
		if (p.hasSplit()) {
			showPlayerHand(p, h, p.getCurrentIndex() + 1);
		} else {
			showPlayerHand(p, h, false);
		}
		if (h.isBusted()) {
			if (p.hasSplit()){
				showBusted(p, p.getCurrentIndex() + 1);
			} else {
				showBusted(p, 0);
			}
		}
	}
	public void showBusted(BlackjackPlayer p, int index) {
        if (index == 0){
            bot.sendMessage(channel, p.getNickStr() + " has busted!");
        } else {
            bot.sendMessage(channel, p.getNickStr()+"-" + index + " has busted!");
        }
	}
	public void showBlackjack(BlackjackPlayer p, int index) {
        if (index == 0){
            bot.sendMessage(channel, p.getNickStr() + " has blackjack!");
        } else {
            bot.sendMessage(channel, p.getNickStr() + "-" + index + " has blackjack!");
        }
	}
	/**
     * Displays the dealt hands of the players and the dealer.
     * @param dealing
     */
    public void showTableHands(boolean dealing) {
		BlackjackPlayer p;
        if (dealing){
            bot.sendMessage(channel, formatHeader(" Dealing Table... "));
        } else {
            bot.sendMessage(channel, formatHeader(" Table: "));
        }
		for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
			p = (BlackjackPlayer) getJoined(ctr);
			for (int ctr2 = 0; ctr2 < p.getNumberHands(); ctr2++){
				if (p.hasSplit()) {
					showPlayerHand(p, p.getHand(ctr2), ctr2+1);
				} else {
					showPlayerHand(p, p.getHand(ctr2), false);
				}
			}
		}
		showPlayerHand(dealer, dealer.getHand(), false);
	}
	/**
     * Displays the final results of the round.
     */
    public void showResults() {
		BlackjackPlayer p;
		BlackjackHand h;
		bot.sendMessage(channel, formatHeader(" Results: "));
		showDealerResult();
		for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
			p = (BlackjackPlayer) getJoined(ctr);
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
		bot.sendMessage(channel, formatHeader(" Insurance Results: "));
		if (dealer.getHand().isBlackjack()) {
			bot.sendMessage(channel, dealer.getNickStr() + " had blackjack.");
		} else {
			bot.sendMessage(channel, dealer.getNickStr() + " did not have blackjack.");
		}

		for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
			p = (BlackjackPlayer) getJoined(ctr);
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
		String outStr; 
		if (dHand.isBlackjack()) {
			outStr = dealer.getNickStr() + " has blackjack (";
		} else {
			outStr = dealer.getNickStr() + " has " + dHand.calcSum() + " (";
		}
		outStr += dHand.toString() + ").";
		bot.sendMessage(channel, outStr);
	}
	/**
     * Displays the result of a player's hand.
     * @param p the player to show
     * @param h the player's hand of which the results are to be shown
     * @param index the hand index if the player has split
     */
    public void showPlayerResult(BlackjackPlayer p, BlackjackHand h, int index) {
		String outStr, nickStr;
		if (index > 0){
			nickStr = p.getNickStr() + "-" + index;
		} else {
			nickStr = p.getNickStr();
		}
		int result = h.compareTo(dealer.getHand());
		int sum = h.calcSum();
		if (p.hasSurrendered()) {
			outStr = getSurrenderStr() + ": " + nickStr + " has " + sum + 
                    " (" + h.toString() + ").";
		} else {
            switch (result) {
                case 2: // Blackjack win
                    outStr = getWinStr() + ": " + nickStr + " has blackjack (" + 
                            h.toString()+") and wins $" +
                            formatNumber(calcBlackjackPayout(h)) + "."; break;
                case 1: // Regular win
                    outStr = getWinStr() + ": " + nickStr + " has " + sum +" (" +
                            h.toString() + ") and wins $" + 
                            formatNumber(calcWinPayout(h)) + ".";  break;
                case 0: // Push
                    outStr = getPushStr() + ": " + nickStr + " has " +sum + 
                            " (" + h.toString() + ") " + "and the $" + 
                            formatNumber(h.getBet()) + " bet is returned."; break;
                default: // Loss
                    outStr = getLossStr() + ": " + nickStr+" has " + sum + 
                            " (" + h.toString()+").";
            }
        }
		outStr += " Stack: $" + formatNumber(p.get("cash"));
		bot.sendMessage(channel, outStr);
	}
	/**
     * Displays the result of a player's insurance bet.
     * @param p a player who has made an insurance bet
     */
    public void showPlayerInsuranceResult(BlackjackPlayer p) {
		String outStr;		
		if (dealer.getHand().isBlackjack()) {
			outStr = getWinStr()+": " + p.getNickStr() + " wins $"
					+ formatNumber(calcInsurancePayout(p)) + ".";
		} else {
			outStr = getLossStr()+": " + p.getNickStr() + " loses.";
		}
		outStr += " Stack: $" + formatNumber(p.get("cash"));
		bot.sendMessage(channel, outStr);
	}

	/* Player/nick output methods to simplify messaging/noticing */
	public void infoCountDisabled(String nick){
		bot.sendNotice(nick, "Counting functions are disabled.");
	}
	public void infoNotPair(String nick) {
		bot.sendNotice(nick, "Your hand cannot be split. It is not a pair.");
	}
	public void infoNotDoubleDown(String nick) {
		bot.sendNotice(nick, "You can only double down before hitting!");
	}
	public void infoNotSurrender(String nick) {
		bot.sendNotice(nick, "You cannot surrender after hitting!");
	}
	public void infoNotSurrenderSplit(String nick) {
		bot.sendNotice(nick, "You cannot surrender a split hand!");
	}
	public void infoInsureBetTooHigh(String nick, int max) {
		bot.sendNotice(nick, "Maximum insurance bet is $" + formatNumber(max) + ". Try again.");
	}
	public void infoInsureBetTooLow(String nick){
        bot.sendNotice(nick, "Minimum insurance bet is $1. Try again.");
    }
	public void infoNotInsureNoAce(String nick) {
		bot.sendNotice(nick, "The dealer's upcard is not an ace. You cannot make an insurance bet.");
	}
	public void infoNotInsureHasHit(String nick) {
		bot.sendNotice(nick, "You cannot make an insurance bet after hitting.");
	}
	public void infoNotInsureHasSplit(String nick) {
		bot.sendNotice(nick, "You cannot make an insurance bet after splitting.");
	}
	public void infoAlreadyInsured(String nick) {
		bot.sendNotice(nick, "You have already made an insurance bet.");
	}
    
	/**
     * Informs the player of his hand and the bet on that hand.
     * The information is sent by notice if simple is true and by message if
     * simple is false.
     * 
     * @param p the player
     * @param h the hand
     */
    public void infoPlayerHand(BlackjackPlayer p, BlackjackHand h) {
		if (p.isSimple()) {
			bot.sendNotice(p.getNick(), 
					"Your current hand is " + h.toString(0) + " with a bet of $"+
                    formatNumber(h.getBet())+".");
		} else {
			bot.sendMessage(p.getNick(),
					"Your current hand is " + h.toString(0) + " with a bet of "+
                    formatNumber(h.getBet())+".");
		}
	}
    
	/**
     * Informs the player of the sum of his hand.
     * The information is sent by notice if simple is true and by message if
     * simple is false.
     * 
     * @param p the player
     * @param h the hand
     */
    public void infoPlayerSum(BlackjackPlayer p, BlackjackHand h) {
		if (p.isSimple()) {
			bot.sendNotice(p.getNick(), "Hand sum is " + h.calcSum() + ".");
		} else {
			bot.sendMessage(p.getNick(), "Hand sum is " + h.calcSum() + ".");
		}
	}
    
	/**
     * Informs a player of the bet on his hand.
     * The information is sent by notice, if simple is true and by message if
     * simple is false.
     * 
     * @param p the player
     * @param h the hand
     */
    public void infoPlayerBet(BlackjackPlayer p, BlackjackHand h) {
		String outStr = "You have bet $"+ formatNumber(h.getBet())+ " on this hand.";
		outStr += ".";
		if (p.isSimple()) {
			bot.sendNotice(p.getNick(), outStr);
		} else {
			bot.sendMessage(p.getNick(), outStr);
		}
	}

	/* Formatted strings */
	@Override
	public String getGameRulesStr() {
        String str;
        if (isSoft17Hit()){
            str = "Dealer hits on soft 17. ";
        } else {
            str = "Dealer stands on soft 17. ";
        }
		return  str + "The dealer's shoe has " + 
                deck.getNumberDecks() + " deck(s) of cards. Discards are " +
                "merged back into the shoe and the shoe is shuffled when " +
                getShufflePoint() + " card(s) remain in the shoe. Regular " + 
                "wins are paid out at 1:1 and blackjacks are paid out at 3:2. " +
                "Insurance wins are paid out at 2:1. Minimum bet is $" + 
                getMinBet() + " or your stack, whichever is lower.";
	}
	@Override
	public String getGameCommandStr() {
		return "go, join, quit, bet, hit, stand, doubledown, surrender, insure, " +
               "split, table, turn, sum, hand, allhands, cash, netcash, bank, " +
               "transfer, deposit, withdraw, bankrupts, winnings, winrate, " +
               "rounds, player, numdecks, numcards, numdiscards, hilo, zen, " +
               "red7, count, simple, players, stats, house, waitlist, " +
               "blacklist, top, game, ghelp, grules, gcommands";
	}
	private static String getWinStr(){
    	return Colors.GREEN+",01"+" WIN "+Colors.NORMAL;
    }
    private static String getLossStr(){
    	return Colors.RED+",01"+" LOSS "+Colors.NORMAL;
    }
	private static String getSurrenderStr(){
		return Colors.RED+",01"+" SURR "+Colors.NORMAL;
	}
	private static String getPushStr(){
		return Colors.WHITE+",01"+" PUSH "+Colors.NORMAL;
	}
}