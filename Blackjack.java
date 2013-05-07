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

import java.util.*;
import java.io.*;

import org.pircbotx.*;
import org.pircbotx.hooks.events.*;

public class Blackjack extends CardGame {
	public static class RespawnTask extends TimerTask {
		Player player;
		Blackjack game;

		public RespawnTask(Player p, Blackjack g) {
			player = p;
			game = g;
		}

		@Override
		public void run() {
			ArrayList<Timer> timers = game.getRespawnTimers();
			player.setCash(game.getNewCash());
			player.addDebt(game.getNewCash());
			game.bot.sendMessage(game.channel, player.getNickStr()
					+ " has been loaned $" + String.format("%,d", game.getNewCash())
					+ ". Please bet responsibly.");
			game.blacklist.remove(player);
			game.savePlayerData(player);
			timers.remove(this);
		}
	}

	public static class IdleOutTask extends TimerTask {
		BlackjackPlayer player;
		Blackjack game;

		public IdleOutTask(BlackjackPlayer p, Blackjack g) {
			player = p;
			game = g;
		}

		@Override
		public void run() {
			if (player == game.getCurrentPlayer()) {
				player.setIdledOut(true);
				game.bot.sendMessage(game.channel, player.getNickStr()
						+ " has wasted precious time and idled out.");
				if (game.isInProgress() && !game.isBetting()) {
					game.stay();
				} else {
					game.leaveGame(player.getUser());
				}
			}
		}
	}

	public static class IdleShuffleTask extends TimerTask {
		Blackjack game;

		public IdleShuffleTask(Blackjack g) {
			game = g;
		}

		@Override
		public void run() {
			game.shuffleShoe();
		}
	}

	public static class HouseStat {
		int numDecks, numRounds, cash;

		public HouseStat() {
			this(0, 0, 0);
		}

		public HouseStat(int a, int b, int c) {
			numDecks = a;
			numRounds = b;
			cash = c;
		}

		public int getNumDecks() {
			return numDecks;
		}

		public int getNumRounds() {
			return numRounds;
		}

		public int getCash() {
			return cash;
		}

		public void addCash(int amount) {
			cash += amount;
		}

		public void incrementNumRounds() {
			numRounds++;
		}

		public String toString() {
			return numRounds + " round(s) have been played using " + numDecks
					+ " deck shoes. The house has won $" + String.format("%,d", cash)
					+ " during those round(s).";
		}
	}

	private BlackjackPlayer dealer;
	private boolean insuranceBets, countEnabled;
	private int shoeDecks, idleShuffleTime;
	private Timer idleShuffleTimer;
	private ArrayList<Timer> respawnTimers;
	private ArrayList<HouseStat> stats;
	private HouseStat house;

	/**
	 * Class constructor for Blackjack, a subclass of CardGame.
	 * 
	 * @param parent
	 *            the bot that creates an instance of this ListenerAdapter
	 * @param gameChannel
	 *            the IRC channel in which the game is to be run.
	 */
	public Blackjack(PircBotX parent, Channel gameChannel) {
		super(parent, gameChannel);
		gameName = "Blackjack";
		dealer = new BlackjackPlayer(bot.getUserBot(), true);
		stats = new ArrayList<HouseStat>();
		respawnTimers = new ArrayList<Timer>();
		loadHouseStats();
		loadSettings();
		insuranceBets = false;
	}

	@Override
	public void onPart(PartEvent event) {
		User user = event.getUser();
		leaveGame(user);
	}

	@Override
	public void onQuit(QuitEvent event) {
		User user = event.getUser();
		leaveGame(user);
	}

	@Override
	public void onMessage(MessageEvent event) {
		String msg = event.getMessage().toLowerCase();

		if (msg.charAt(0) == '.') {
			User user = event.getUser();

			/* Parsing commands from the channel */
			if (msg.equals(".join") || msg.equals(".j")) {
				if (playerJoined(user)) {
					infoAlreadyJoined(user);
				} else if (isBlacklisted(user)) {
					infoBlacklisted(user);
				} else if (isInProgress()) {
					if (playerWaiting(user)) {
						infoAlreadyWaiting(user);
					} else {
						addWaitingPlayer(user);
					}
				} else {
					addPlayer(user);
				}
			} else if (msg.equals(".leave") || msg.equals(".quit")
					|| msg.equals(".l") || msg.equals(".q")) {
				if (!playerJoined(user) && !playerWaiting(user)) {
					infoNotJoined(user);
				} else if (playerWaiting(user)) {
					removeWaiting(user);
				} else {
					leaveGame(user);
				}
			} else if (msg.equals(".start") || msg.equals(".go")) {
				if (!playerJoined(user)) {
					infoNotJoined(user);
				} else if (isInProgress()) {
					infoRoundStarted(user);
				} else if (getNumberPlayers() > 0) {
					cancelIdleShuffleTimer();
					showStartRound();
					showPlayers();
					setInProgress(true);
					setBetting(true);
					setStartRoundTimer();
				} else {
					showNoPlayers();
				}
			} else if (msg.startsWith(".bet ") || msg.startsWith(".b ")
					|| msg.equals(".bet") || msg.equals(".b")) {
				if (!playerJoined(user)) {
					infoNotJoined(user);
				} else if (!isInProgress()) {
					infoNotStarted(user);
				} else if (!isBetting()) {
					bot.sendNotice(user, "No betting in progress!");
				} else if (!(currentPlayer == findPlayer(user))) {
					bot.sendNotice(user, "It's not your turn!");
				} else {
					try {
						try {
							int amount = parseNumberParam(msg);
							bet(amount);
						} catch (NumberFormatException e) {
							infoImproperParameter(user);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(user);
					}
				}
			} else if (msg.equals(".hit") || msg.equals(".h")) {
				if (!playerJoined(user)) {
					bot.sendNotice(user, "You are not currently joined!");
				} else if (!isInProgress()) {
					bot.sendNotice(user, "No round in progress!");
				} else if (isBetting()) {
					bot.sendNotice(user, "No cards have been dealt yet!");
				} else if (!(currentPlayer == findPlayer(user))) {
					bot.sendNotice(user, "It's not your turn!");
				} else {
					hit();
				}
			} else if (msg.equals(".stay") || msg.equals(".stand")
					|| msg.equals(".sit")) {
				if (!playerJoined(user)) {
					bot.sendNotice(user, "You are not currently joined!");
				} else if (!isInProgress()) {
					bot.sendNotice(user, "No round in progress!");
				} else if (isBetting()) {
					bot.sendNotice(user, "No cards have been dealt yet!");
				} else if (!(currentPlayer == findPlayer(user))) {
					bot.sendNotice(user, "It's not your turn!");
				} else {
					stay();
				}
			} else if (msg.equals(".doubledown") || msg.equals(".dd")) {
				if (!playerJoined(user)) {
					bot.sendNotice(user, "You are not currently joined!");
				} else if (!isInProgress()) {
					bot.sendNotice(user, "No round in progress!");
				} else if (isBetting()) {
					bot.sendNotice(user, "No cards have been dealt yet!");
				} else if (!(currentPlayer == findPlayer(user))) {
					bot.sendNotice(user, "It's not your turn!");
				} else {
					doubleDown();
				}
			} else if (msg.equals(".surrender") || msg.equals(".surr")) {
				if (!playerJoined(user)) {
					bot.sendNotice(user, "You are not currently joined!");
				} else if (!isInProgress()) {
					bot.sendNotice(user, "No round in progress!");
				} else if (isBetting()) {
					bot.sendNotice(user, "No cards have been dealt yet!");
				} else if (!(currentPlayer == findPlayer(user))) {
					bot.sendNotice(user, "It's not your turn!");
				} else {
					surrender();
				}
			} else if (msg.startsWith(".insure ") || msg.startsWith(".insure")) {
				if (!playerJoined(user)) {
					bot.sendNotice(user, "You are not currently joined!");
				} else if (!isInProgress()) {
					bot.sendNotice(user, "No round in progress!");
				} else if (isBetting()) {
					bot.sendNotice(user, "No cards have been dealt yet!");
				} else if (!(currentPlayer == findPlayer(user))) {
					bot.sendNotice(user, "It's not your turn!");
				} else {
					try {
						try {
							int amount = parseNumberParam(msg);
							insure(amount);
						} catch (NumberFormatException e) {
							infoImproperParameter(user);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(user);
					}
				}
			} else if (msg.equals(".split")) {
				if (!playerJoined(user)) {
					bot.sendNotice(user, "You are not currently joined!");
				} else if (!isInProgress()) {
					bot.sendNotice(user, "No round in progress!");
				} else if (isBetting()) {
					bot.sendNotice(user, "No cards have been dealt yet!");
				} else if (!(currentPlayer == findPlayer(user))) {
					bot.sendNotice(user, "It's not your turn!");
				} else {
					split();
				}
			} else if (msg.equals(".table")) {
				if (!playerJoined(user)) {
					bot.sendNotice(user, "You are not currently joined!");
				} else if (!isInProgress()) {
					bot.sendNotice(user, "No round in progress!");
				} else if (isBetting()) {
					bot.sendNotice(user, "No cards have been dealt yet!");
				} else {
					showTableHands();
				}
			} else if (msg.equals(".sum")) {
				if (!playerJoined(user)) {
					bot.sendNotice(user, "You are not currently joined!");
				} else if (!isInProgress()) {
					bot.sendNotice(user, "No round in progress!");
				} else if (isBetting()) {
					bot.sendNotice(user, "No cards have been dealt yet!");
				} else {
					BlackjackPlayer p = (BlackjackPlayer) findPlayer(user);
					infoPlayerSum(p, p.getCurrentHand());
				}
				/* Contributed by Yky */
			} else if (msg.equals(".zc") || (msg.equals(".zen"))) {
				if (!playerJoined(user)) {
					bot.sendNotice(user, "You are not currently joined!");
				} else if (isInProgress()) {
					bot.sendNotice(user, "A round is in progress! Wait until the round is over.");
				} else if (!isCountEnabled()) {
					bot.sendNotice(user, "Counting functions are disabled.");
				} else {
					bot.sendNotice(user, "Zen count = " + getZenCount());
				}
				/* Contributed by Yky */
			} else if (msg.equals(".hc") || (msg.equals(".hilo"))) {
				if (!playerJoined(user)) {
					bot.sendNotice(user, "You are not currently joined!");
				} else if (isInProgress()) {
					bot.sendNotice(user, "A round is in progress! Wait until the round is over.");
				} else if (!isCountEnabled()) {
					bot.sendNotice(user, "Counting functions are disabled.");
				} else {
					bot.sendNotice(user, "Hi-Lo count = " + getHiLo());
				}
				/* Contributed by Yky */
			} else if (msg.equals(".rc") || (msg.equals(".red7"))) {
				if (!playerJoined(user)) {
					bot.sendNotice(user, "You are not currently joined!");
				} else if (isInProgress()) {
					bot.sendNotice(user, "A round is in progress! Wait until the round is over.");
				} else if (!isCountEnabled()) {
					bot.sendNotice(user, "Counting functions are disabled.");
				} else {
					bot.sendNotice(user, "Red7 count = " + getRed7());
				}
			} else if (msg.equals(".hand")) {
				if (!playerJoined(user)) {
					bot.sendNotice(user, "You are not currently joined!");
				} else if (!isInProgress()) {
					bot.sendNotice(user, "No round in progress!");
				} else if (isBetting()) {
					bot.sendNotice(user, "No cards have been dealt yet!");
				} else {
					BlackjackPlayer p = (BlackjackPlayer) findPlayer(user);
					infoPlayerHand(p, p.getCurrentHand());
				}
			} else if (msg.equals(".allhands")) {
				bot.sendNotice(user,
						"This command has not been implemented yet.");
			} else if (msg.equals(".turn")) {
				if (!playerJoined(user)) {
					bot.sendNotice(user, "You are not currently joined!");
				} else if (!isInProgress()) {
					bot.sendNotice(user, "No round in progress!");
				} else {
					showPlayerTurn(currentPlayer);
				}
			} else if (msg.equals(".simple")) {
				if (!playerJoined(user)) {
					bot.sendNotice(user, "You are not currently joined!");
				} else {
					togglePlayerSimple(user);
				}
			} else if (msg.startsWith(".cash ") || msg.equals(".cash")) {
				try {
					String nick = parseStringParam(msg);
					showPlayerCash(nick);
				} catch (NoSuchElementException e) {
					showPlayerCash(user.getNick());
				}
			} else if (msg.startsWith(".netcash ") || msg.equals(".netcash") ||
					msg.startsWith(".net ") || msg.equals(".net")) {
				try {
					String nick = parseStringParam(msg);
					showPlayerNetCash(nick);
				} catch (NoSuchElementException e) {
					showPlayerNetCash(user.getNick());
				}
			} else if (msg.startsWith(".debt ") || msg.equals(".debt")) {
				try {
					String nick = parseStringParam(msg);
					showPlayerDebt(nick);
				} catch (NoSuchElementException e) {
					showPlayerDebt(user.getNick());
				}
			} else if (msg.startsWith(".bankrupts ") || msg.equals(".bankrupts")) {
				try {
					String nick = parseStringParam(msg);
					showPlayerBankrupts(nick);
				} catch (NoSuchElementException e) {
					showPlayerBankrupts(user.getNick());
				}
			} else if (msg.startsWith(".rounds ") || msg.equals(".rounds")){
				try {
					String nick = parseStringParam(msg);
					showPlayerRounds(nick);
				} catch (NoSuchElementException e) {
					showPlayerRounds(user.getNick());
				}
			} else if (msg.startsWith(".paydebt ")) {
				if (!playerJoined(user)) {
					bot.sendNotice(user, "You are not currently joined!");
				} else if (isInProgress()) {
					bot.sendNotice(user,
							"A round is in progress! Wait until the round is over.");
				} else {
					try {
						try {
							int amount = parseNumberParam(msg);
							payPlayerDebt(user, amount);
						} catch (NumberFormatException e) {
							infoImproperParameter(user);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(user);
					}
				}
			} else if (msg.startsWith(".house ") || msg.equals(".house")) {
				if (isInProgress()) {
					bot.sendNotice(user,
							"A round is in progress! Wait until the round is over.");
				} else {
					try {
						try {
							int ndecks = parseNumberParam(msg);
							showHouseStat(ndecks);
						} catch (NumberFormatException e) {
							infoImproperParameter(user);
						}
					} catch (NoSuchElementException e) {
						showHouseStat(shoeDecks);
					}
				}
			} else if (msg.equals(".players")) {
				showPlayers();
			} else if (msg.equals(".waitlist")) {
				showWaiting();
			} else if (msg.equals(".blacklist")) {
				showBankrupt();
			} else if (msg.startsWith(".top5 ") || msg.equals(".top5")) {
				if (isInProgress()) {
					infoWaitRoundEnd(user);
				} else {
					try {
						try {
							String param = parseStringParam(msg).toLowerCase();
							showTopPlayers(param,5);
						} catch (IllegalArgumentException e) {
							infoImproperParameter(user);
						}
					} catch (NoSuchElementException e) {
						showTopPlayers("cash",5);
					}
				}
			} else if (msg.equals(".gamerules") || msg.equals(".grules")) {
				infoGameRules(user);
			} else if (msg.equals(".gamehelp") || msg.equals(".ghelp")) {
				infoGameHelp(user);
			} else if (msg.equals(".gamecommands") || msg.equals(".gcommands")) {
				infoGameCommands(user);
			} else if (msg.equals(".currentgame") || msg.equals(".game")) {
				showGameName();
			} else if (msg.equals(".numdecks") || msg.equals(".ndecks")) {
				infoNumDecks(user);
			} else if (msg.equals(".numcards") || msg.equals(".ncards")) {
				infoNumCards(user);
			} else if (msg.equals(".numdiscards") || msg.equals(".ndiscards")) {
				infoNumDiscards(user);
			/* Op/Debugging commands */
			} else if (msg.startsWith(".cards ") || msg.startsWith(".discards ")
					|| msg.equals(".cards") || msg.equals(".discards")) {
				if (channel.isOp(user)) {
					try {
						try {
							int num = parseNumberParam(msg);
							if (msg.startsWith(".cards ")
									&& deck.getNumberCards() > 0) {
								infoDeckCards(user, 'c', num);
							} else if (msg.startsWith(".discards ")
									&& deck.getNumberDiscards() > 0) {
								infoDeckCards(user, 'd', num);
							} else {
								bot.sendNotice(user, "Empty!");
							}
						} catch (NumberFormatException e) {
							infoImproperParameter(user);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(user);
					}
				} else {
					infoOpsOnly(user);
				}
			} else if (msg.equals(".shuffle")) {
				if (isInProgress()) {
					infoWaitRoundEnd(user);
				} else if (channel.isOp(user)) {
					cancelIdleShuffleTimer();
					shuffleShoe();
				} else {
					infoOpsOnly(user);
				}
			} else if (msg.equals(".reload")) {
				if (isInProgress()) {
					infoWaitRoundEnd(user);
				} else if (channel.isOp(user)) {
					cancelIdleShuffleTimer();
					loadSettings();
					showReloadSettings();
				} else {
					infoOpsOnly(user);
				}
			} else if (msg.startsWith(".set ") || msg.equals(".set")){
				if (isInProgress()) {
					infoWaitRoundEnd(user);
				} else if (channel.isOp(user)) {
					try {
						try {
							String[] iniParams = parseIniParams(msg);
							setSetting(iniParams);
							showUpdateSetting(iniParams[0]);
						} catch (IllegalArgumentException e) {
							infoImproperParameter(user);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(user);
					}
				} else {
					infoOpsOnly(user);
				}
			}
		}
	}

	/* Accessor methods */
	public void setShoeDecks(int value) {
		shoeDecks = value;
	}
	public int getShoeDecks() {
		return shoeDecks;
	}
	public void setCountEnabled(boolean value){
		countEnabled = value;
	}
	public boolean isCountEnabled(){
    	return countEnabled;
    }
	public void setIdleShuffleTime(int value){
		idleShuffleTime = value;
	}
	public int getIdleShuffleTime() {
		return idleShuffleTime;
	}

	/* Game management methods */
	@Override
	public void setSetting(String[] params) throws IllegalArgumentException {
		String setting = params[0];
		String value = params[1];
		if (setting.equals("decks")){
			setShoeDecks(Integer.parseInt(value));
			deck = new CardDeck(shoeDecks);
			deck.shuffleCards();
			house = getHouseStat(shoeDecks);
			if (house == null) {
				house = new HouseStat(shoeDecks, 0, 0);
				stats.add(house);
			}
			saveSettings();
		} else if (setting.equals("idle")){
			setIdleOutTime(Integer.parseInt(value));
			saveSettings();
		} else if (setting.equals("idleshuffle")){
			setIdleShuffleTime(Integer.parseInt(value));
			saveSettings();
		} else if (setting.equals("cash")){
			setNewCash(Integer.parseInt(value));
			saveSettings();
		} else if (setting.equals("respawn")){
			setRespawnTime(Integer.parseInt(value));
			saveSettings();
		} else if (setting.equals("count")){
			setCountEnabled(Boolean.parseBoolean(value));
			saveSettings();
		} else {
			throw new IllegalArgumentException();
		}
	}
	@Override
	public void loadSettings() {
		try {
			BufferedReader f = new BufferedReader(new FileReader("blackjack.ini"));
			String str, name, value;
			StringTokenizer st;
			while (f.ready()) {
				str = f.readLine();
				if (str.startsWith("#")) {
					continue;
				}
				st = new StringTokenizer(str, "=");
				name = st.nextToken();
				value = st.nextToken();
				if (name.equals("decks")) {
					shoeDecks = Integer.parseInt(value);
				} else if (name.equals("idle")) {
					idleOutTime = Integer.parseInt(value) * 1000;
				} else if (name.equals("idleshuffle")) {
					idleShuffleTime = Integer.parseInt(value) * 1000;
				} else if (name.equals("cash")) {
					newcash = Integer.parseInt(value);
				} else if (name.equals("respawn")) {
					respawnTime = Integer.parseInt(value) * 1000;
				} else if (name.equals("count")){
					countEnabled = Boolean.parseBoolean(value);
				}
			}
			f.close();
		} catch (IOException e) {
			/* load defaults if blackjack.ini is not found */
			System.out.println("blackjack.ini not found! Creating new blackjack.ini...");
			shoeDecks = 1;
			newcash = 1000;
			idleOutTime = 60000;
			respawnTime = 600000;
			idleShuffleTime = 300000;
			countEnabled = true;
			saveSettings();			
		}
		deck = new CardDeck(shoeDecks);
		deck.shuffleCards();
		house = getHouseStat(shoeDecks);
		if (house == null) {
			house = new HouseStat(shoeDecks, 0, 0);
			stats.add(house);
		}
	}
	@Override
	public void saveSettings(){
		try {
			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("blackjack.ini")));
			out.println("#Settings");
			out.println("#Number of decks in the dealer's shoe");
			out.println("decks=" + shoeDecks);
			out.println("#Number of seconds before a player idles out");
			out.println("idle=" + idleOutTime / 1000);
			out.println("#Number of seconds of idleness after a round ends before the deck is shuffled");
			out.println("idleshuffle=" + idleShuffleTime / 1000);
			out.println("#Initial amount given to new and bankrupt players");
			out.println("cash=" + newcash);
			out.println("#Number of seconds before a bankrupt player is allowed to join again");
			out.println("respawn=" + respawnTime / 1000);
			out.println("#Whether card counting functions are enabled");
			out.println("count=" + countEnabled);
			out.close();
		} catch (IOException f) {
			System.out.println("Error creating blackjack.ini!");
		}
	}

	public void loadHouseStats() {
		try {
			BufferedReader f = new BufferedReader(new FileReader(
					"housestats.txt"));
			String str;
			int ndecks, nrounds, winnings;
			StringTokenizer st;
			while (f.ready()) {
				str = f.readLine();
				if (str.startsWith("#")) {
					if (str.contains("blackjack")) {
						while (f.ready()) {
							str = f.readLine();
							if (str.startsWith("#")) {
								break;
							}
							st = new StringTokenizer(str);
							ndecks = Integer.parseInt(st.nextToken());
							nrounds = Integer.parseInt(st.nextToken());
							winnings = Integer.parseInt(st.nextToken());
							stats.add(new HouseStat(ndecks, nrounds, winnings));
						}
						break;
					}
				}
			}
			f.close();
		} catch (IOException e) {
			System.out
					.println("housestats.txt not found! Creating new housestats.txt...");
			try {
				PrintWriter out = new PrintWriter(new BufferedWriter(
						new FileWriter("housestats.txt")));
				out.close();
			} catch (IOException f) {
				System.out.println("Error creating housestats.txt!");
			}
		}
	}

	public void saveHouseStats() {
		boolean found = false;
		int index = 0;
		ArrayList<String> lines = new ArrayList<String>();
		try {
			BufferedReader f = new BufferedReader(new FileReader(
					"housestats.txt"));
			String str;
			while (f.ready()) {
				str = f.readLine();
				lines.add(str);
				if (str.startsWith("#blackjack")) {
					found = true;
					index = lines.size();
					while (f.ready()) {
						str = f.readLine();
						if (str.startsWith("#")) {
							lines.add(str);
							break;
						}
					}
				}
			}
			f.close();
		} catch (IOException e) {
			/* housestats.txt is not found */
			System.out.println("Error reading housestats.txt!");
		}
		if (!found) {
			lines.add("#blackjack");
			index = lines.size();
		}
		for (int ctr = 0; ctr < stats.size(); ctr++) {
			lines.add(index, stats.get(ctr).getNumDecks() + " "
					+ stats.get(ctr).getNumRounds() + " "
					+ stats.get(ctr).getCash());
		}
		try {
			PrintWriter out = new PrintWriter(new BufferedWriter(
					new FileWriter("housestats.txt")));
			for (int ctr = 0; ctr < lines.size(); ctr++) {
				out.println(lines.get(ctr));
			}
			out.close();
		} catch (IOException f) {
			System.out.println("Error writing to housestats.txt!");
		}
	}

	public HouseStat getHouseStat(int numDecks) {
		HouseStat hs;
		for (int ctr = 0; ctr < stats.size(); ctr++) {
			hs = stats.get(ctr);
			if (hs.getNumDecks() == numDecks) {
				return hs;
			}
		}
		return null;
	}
	@Override
	public void loadPlayerData(Player p){
        try{
        	boolean found = false;
            ArrayList<String> nicks = new ArrayList<String>();
            ArrayList<Boolean> simples = new ArrayList<Boolean>();
            ArrayList<Integer> stacks = new ArrayList<Integer>();
            ArrayList<Integer> bankrupts = new ArrayList<Integer>();
            ArrayList<Integer> debts = new ArrayList<Integer>();
            ArrayList<Integer> bjrounds = new ArrayList<Integer>();
        	loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, simples);
        	int numLines = nicks.size();
        	for (int ctr = 0; ctr < numLines; ctr++){
        		if (p.getNick().toLowerCase().equals(nicks.get(ctr))){
                    if (stacks.get(ctr) <= 0){
                    	p.setCash(getNewCash());
                    } else {
                    	p.setCash(stacks.get(ctr));
                    }
                    p.setDebt(debts.get(ctr));
                    p.setBankrupts(bankrupts.get(ctr));
                    p.setRounds(bjrounds.get(ctr));
                    p.setSimple(simples.get(ctr));
                    found = true;
                    break;
                }
        	}
        	if (!found){
                p.setCash(getNewCash());
                p.setDebt(0);
                p.setBankrupts(0);
                p.setRounds(0);
                p.setSimple(true);
                infoNewPlayer(p);
            }
        } catch (IOException e){
        	System.out.println("Error reading players.txt!");
        }
    }
	@Override
	public void savePlayerData(Player p){
        boolean found = false;
        ArrayList<String> nicks = new ArrayList<String>();
        ArrayList<Integer> stacks = new ArrayList<Integer>();
        ArrayList<Integer> debts = new ArrayList<Integer>();
        ArrayList<Integer> bankrupts = new ArrayList<Integer>();
        ArrayList<Integer> bjrounds = new ArrayList<Integer>();
        ArrayList<Boolean> simples = new ArrayList<Boolean>();
        int numLines;
        try{
        	loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, simples);
        	numLines = nicks.size();
        	for (int ctr = 0; ctr < numLines; ctr++){
        		if (p.getNick().toLowerCase().equals(nicks.get(ctr))){
                    stacks.set(ctr, p.getCash());
                    debts.set(ctr, p.getDebt());
                    bankrupts.set(ctr, p.getBankrupts());
                    bjrounds.set(ctr, p.getRounds());
                    simples.set(ctr, p.isSimple());
                    found = true;
                }
        	}
        	if (!found){
                nicks.add(p.getNick());
                stacks.add(p.getCash());
                debts.add(p.getDebt());
                bankrupts.add(p.getBankrupts());
                bjrounds.add(p.getRounds());
                simples.add(p.isSimple());
            }
        } catch (IOException e){
        	System.out.println("Error reading players.txt!");
        }
        
        try {
            savePlayerFile(nicks, stacks, debts, bankrupts, bjrounds, simples);
        } catch (IOException e){
        	System.out.println("Error writing to players.txt!");
        }
    }
	public int getPlayerRounds(String nick){
    	if (playerJoined(nick)){
    		return findPlayer(nick).getRounds();
    	} else {
    		return loadPlayerStat(nick, "bjrounds");
    	}
    }
	
	@Override
	public void leaveGame(User u) {
		if (playerJoined(u)) {
			Player p = findPlayer(u);
			if (isInProgress()) {
				if (p == currentPlayer) {
					currentPlayer = getNextPlayer();
					removePlayer(u);
					if (currentPlayer == null) {
						if (isBetting()) {
							setBetting(false);
							if (getNumberPlayers() == 0) {
								endRound();
							} else {
								dealTable();
								currentPlayer = players.get(0);
								quickEval();
							}
						} else {
							endRound();
						}
					} else {
						showPlayerTurn(currentPlayer);
					}
				} else {
					removePlayer(u);
				}
			} else {
				removePlayer(u);
			}
		}
	}

	@Override
	public void startRound() {
		if (getNumberPlayers() > 0) {
			currentPlayer = players.get(0);
			showPlayerTurn(currentPlayer);
			setIdleOutTimer();
		} else {
			endRound();
		}
	}

	@Override
	public void endRound() {
		BlackjackPlayer p;
		Hand dHand;
		setInProgress(false);
		if (getNumberPlayers() > 0) {
			house.incrementNumRounds();
			showPlayerTurn(dealer);
			dHand = dealer.getCurrentHand();
			showPlayerHand(dealer, dHand, true);
			if (needDealerHit()) {
				while (getCardSum(dHand) < 17) {
					dealOne(dHand);
					showPlayerHand(dealer, dHand, true);
				}
			}
			if (isHandBlackjack(dHand)) {
				showBlackjack(dealer);
			} else if (isHandBusted(dHand)) {
				showBusted(dealer);
			}
			showResults();
			if (hasInsuranceBets()) {
				showInsuranceResults();
			}
			for (int ctr = 0; ctr < getNumberPlayers(); ctr++) {
				p = (BlackjackPlayer) getPlayer(ctr);
				p.incrementRounds();
				if (isPlayerBankrupt(p)) {
					p.incrementBankrupts();
					blacklist.add(p);
					infoPlayerBankrupt(p.getUser());
					bot.sendMessage(
							channel,
							p.getNickStr()
									+ " has gone bankrupt. S/He has been kicked to the curb.");
					removePlayer(p.getUser());
					setRespawnTimer(p);
					ctr--;
				}
			}
		} else {
			showNoPlayers();
		}
		resetGame();
		showEndRound();
		showSeparator();
		removeIdlers();
		addWaitingPlayers();
		if (deck.getNumberDiscards() > 0) {
			setIdleShuffleTimer();
		}
	}

	@Override
	public void endGame() {
		cancelStartRoundTimer();
		cancelIdleOutTimer();
		cancelIdleShuffleTimer();
		cancelRespawnTimers();
		saveAllPlayers();
		saveHouseStats();
		saveSettings();
		players.clear();
		waitlist.clear();
		blacklist.clear();
		deck = null;
		dealer = null;
		currentPlayer = null;
	}

	@Override
	public void resetGame() {
		discardPlayerHand(dealer);
		setInsuranceBets(false);
		resetPlayers();
	}

	@Override
	public void resetPlayers() {
		BlackjackPlayer p;
		for (int ctr = 0; ctr < getNumberPlayers(); ctr++) {
			p = (BlackjackPlayer) players.get(ctr);
			discardPlayerHand(p);
			p.resetCurrentIndex();
			p.clearInitialBet();
		}
	}

	@Override
	public void setIdleOutTimer() {
		idleOutTimer = new Timer();
		idleOutTimer.schedule(new IdleOutTask((BlackjackPlayer) currentPlayer,
				this), idleOutTime);
	}

	@Override
	public void cancelIdleOutTimer() {
		if (idleOutTimer != null) {
			idleOutTimer.cancel();
			idleOutTimer = null;
		}
	}

	public void setIdleShuffleTimer() {
		idleShuffleTimer = new Timer();
		idleShuffleTimer.schedule(new IdleShuffleTask(this), idleShuffleTime);
	}

	public void cancelIdleShuffleTimer() {
		if (idleShuffleTimer != null) {
			idleShuffleTimer.cancel();
			idleShuffleTimer = null;
		}
	}

	public void setRespawnTimer(Player p) {
		Timer t = new Timer();
		t.schedule(new RespawnTask(p, this), respawnTime);
		respawnTimers.add(t);
	}

	public void cancelRespawnTimers() {
		if (respawnTimers.size() != 0) {
			for (int ctr = 0; ctr < respawnTimers.size(); ctr++) {
				respawnTimers.get(0).cancel();
				respawnTimers.remove(0);
			}
		}
	}

	public ArrayList<Timer> getRespawnTimers() {
		return respawnTimers;
	}

	public void removeIdlers() {
		Player p;
		for (int ctr = 0; ctr < getNumberPlayers(); ctr++) {
			p = getPlayer(ctr);
			if (isPlayerIdledOut(p)) {
				leaveGame(p.getUser());
				ctr--;
			}
		}
	}

	/* Player management methods */
	@Override
	public void addPlayer(User user) {
		Player p = new BlackjackPlayer(user, false);
		players.add(0, p);
		loadPlayerData(p);
		showPlayerJoin(p);
	}

	@Override
	public void addWaitingPlayer(User user) {
		Player p = new BlackjackPlayer(user, false);
		waitlist.add(p);
		loadPlayerData(p);
		infoPlayerWaiting(p);
	}

	/* Calculations for blackjack */
	public int getCardValue(Card c) {
		int num;
		try {
			num = Integer.parseInt(c.getFace());
		} catch (NumberFormatException e) {
			return 10;
		}
		return num;
	}

	public int getCardSum(Hand h) {
		int sum = 0, numAces = 0;
		int numCards = h.getSize();
		Card card;
		// sum the non-aces first and store number of aces in the hand
		for (int ctr = 0; ctr < numCards; ctr++) {
			card = h.get(ctr);
			if (card.getFace().equals("A")) {
				numAces++;
			} else {
				sum += getCardValue(card);
			}
		}
		// find biggest non-busting sum with aces
		if (numAces > 0) {
			if ((numAces - 1) + 11 + sum > 21) {
				sum += numAces;
			} else {
				sum += (numAces - 1) + 11;
			}
		}
		return sum;
	}

	public int calcHalf(int amount) {
		return (int) (Math.ceil((double) (amount) / 2.));
	}
	/* contributed by Yky */
	private int getZenCount() {
		int zenCount = 0;
		String face;
		ArrayList<Card> discards = deck.getDiscards();
		for (int i = 0; i < deck.getNumberDiscards(); i++) {
			face = discards.get(i).getFace();
			if (face.equals("2") || face.equals("3")){
				zenCount++;
			} else if (face.equals("4") || face.equals("5") || face.equals("6")){
				zenCount += 2;
			} else if (face.equals("7")) {
				zenCount += 1;
			} else if (face.equals("T") || face.equals("J") || face.equals("Q") || face.equals("K")){
				zenCount -= 2;
			} else if (face.equals("A")){
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
			if (face.equals("2") || face.equals("3") || face.equals("4") || face.equals("5") || face.equals("6")){
				hiLo++;
			} else if (face.equals("T") || face.equals("J") || face.equals("Q") || face.equals("K") || face.equals("A")){
				hiLo--;
			}
		}
		return hiLo;
	}
	/* contributed by Yky */
	private double getRed7() {
		double red7 = -2*getShoeDecks();
		String face;
		ArrayList<Card> discards = deck.getDiscards();
		for (int i = 0; i < deck.getNumberDiscards(); i++) {
			face = discards.get(i).getFace();
			if (face.equals("2") || face.equals("3") || face.equals("4") || face.equals("5") || face.equals("6")){
				red7++;
			} else if (face.equals("T") || face.equals("J") || face.equals("Q") || face.equals("K") || face.equals("A")){
				red7--;
			} else if (face.equals("7")) {
				red7 += 0.5;
			}
		}
		return red7;
	}

	/* Card management methods for Blackjack */
	public void shuffleShoe() {
		deck.refillDeck();
		showShuffleShoe();
	}

	public void dealOne(Hand h) {
		h.add(deck.takeCard());
		if (deck.getNumberCards() == 0) {
			showDeckEmpty();
			deck.refillDeck();
		}
	}

	public void dealHand(BlackjackPlayer p) {
		p.addHand();
		Hand h = p.getCurrentHand();
		for (int ctr2 = 0; ctr2 < 2; ctr2++) {
			h.add(deck.takeCard());
			if (deck.getNumberCards() == 0) {
				showDeckEmpty();
				deck.refillDeck();
			}
		}
	}

	public void dealTable() {
		BlackjackPlayer p;
		Hand h;
		for (int ctr = 0; ctr < getNumberPlayers(); ctr++) {
			p = (BlackjackPlayer) players.get(ctr);
			dealHand(p);
			h = p.getCurrentHand();
			h.setBet(p.getInitialBet());
			if (deck.getNumberDecks() == 1) {
				infoPlayerHand(p, h);
			}
		}
		dealHand(dealer);
		showTableHands();
	}

	@Override
	public void discardPlayerHand(Player p) {
		BlackjackPlayer BJp = (BlackjackPlayer) p;
		if (BJp.hasHands()) {
			for (int ctr = 0; ctr < BJp.getNumberHands(); ctr++) {
				deck.addToDiscard(BJp.getHand(ctr).getAllCards());
			}
			BJp.resetHands();
		}
	}

	/* Direct Blackjack command methods */
	public void bet(int amount) {
		cancelIdleOutTimer();
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		if (amount > p.getCash()) {
			infoBetTooHigh(p);
			setIdleOutTimer();
		} else if (amount <= 0) {
			infoBetTooLow(p);
			setIdleOutTimer();
		} else {
			p.setInitialBet(amount);
			p.addCash(-1 * amount);
			house.addCash(amount);
			showProperBet(p);
			currentPlayer = getNextPlayer();
			if (currentPlayer == null) {
				setBetting(false);
				showDealingTable();
				dealTable();
				currentPlayer = players.get(0);
				quickEval();
			} else {
				showPlayerTurn(currentPlayer);
				setIdleOutTimer();
			}
		}
	}

	public void stay() {
		cancelIdleOutTimer();
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		Hand nHand;
		if (p.getNumberHands() > 1
				&& p.getCurrentIndex() < p.getNumberHands() - 1) {
			nHand = p.getNextHand();
			showPlayerHand(p, nHand, p.getCurrentIndex() + 1);
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

	public void hit() {
		cancelIdleOutTimer();
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		Hand nHand, cHand = p.getCurrentHand();
		dealOne(cHand);
		if (p.hasSplit()) {
			showPlayerHand(p, cHand, p.getCurrentIndex() + 1);
		} else {
			showPlayerHand(p, cHand);
		}
		if (isHandBusted(cHand)) {
			showBusted(p);
			if (p.getNumberHands() > 1
					&& p.getCurrentIndex() < p.getNumberHands() - 1) {
				nHand = p.getNextHand();
				showPlayerHand(p, nHand, p.getCurrentIndex() + 1);
				quickEval();
			} else {
				currentPlayer = getNextPlayer();
				if (currentPlayer == null) {
					endRound();
				} else {
					quickEval();
				}
			}
		} else {
			setIdleOutTimer();
		}
	}

	public void doubleDown() {
		cancelIdleOutTimer();
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		Hand nHand, cHand = p.getCurrentHand();
		if (cHand.hasHit()) {
			infoNotDoubleDown(p);
			setIdleOutTimer();
		} else if (p.getInitialBet() > p.getCash()) {
			infoInsufficientFunds(p);
			setIdleOutTimer();
		} else {
			p.addCash(-1 * cHand.getBet());
			house.addCash(cHand.getBet());
			cHand.addBet(cHand.getBet());
			dealOne(cHand);
			showDoubleDown(p, cHand);
			if (p.hasSplit()) {
				showPlayerHand(p, cHand, p.getCurrentIndex() + 1);
			} else {
				showPlayerHand(p, cHand);
			}
			if (isHandBusted(cHand)) {
				showBusted(p);
			}
			if (p.getNumberHands() > 1
					&& p.getCurrentIndex() < p.getNumberHands() - 1) {
				nHand = p.getNextHand();
				showPlayerHand(p, nHand, p.getCurrentIndex() + 1);
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
	}

	public void surrender() {
		cancelIdleOutTimer();
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		Hand nHand, cHand = p.getCurrentHand();
		if (cHand.hasHit()) {
			infoNotSurrender(p);
			setIdleOutTimer();
		} else {
			p.addCash(calcHalf(cHand.getBet()));
			house.addCash(-1 * calcHalf(cHand.getBet()));
			cHand.setSurrender(true);
			showSurrender(p);
			if (p.getNumberHands() > 1
					&& p.getCurrentIndex() < p.getNumberHands() - 1) {
				nHand = p.getNextHand();
				showPlayerHand(p, nHand, p.getCurrentIndex() + 1);
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
	}

	public void insure(int amount) {
		cancelIdleOutTimer();
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		Hand cHand = p.getCurrentHand();
		if (cHand.hasInsured()) {
			infoAlreadyInsured(p);
		} else if (!dealerUpcardAce()) {
			infoNotInsure(p);
		} else if (p.getCash() == 0) {
			infoInsufficientFunds(p);
		} else if (amount > calcHalf(cHand.getBet())) {
			infoInsureBetTooHigh(p);
		} else if (amount <= 0) {
			infoBetTooLow(p);
		} else {
			setInsuranceBets(true);
			cHand.setInsureBet(amount);
			p.addCash(-1 * amount);
			house.addCash(amount);
			showInsure(p, cHand);
		}
		setIdleOutTimer();
	}

	public void split() {
		cancelIdleOutTimer();
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		Hand nHand, cHand = p.getCurrentHand();
		if (!isHandPair(cHand)) {
			infoNotPair(p);
			setIdleOutTimer();
		} else if (p.getCash() < cHand.getBet()) {
			infoInsufficientFunds(p);
			setIdleOutTimer();
		} else {
			p.addCash(-1 * cHand.getBet());
			house.addCash(cHand.getBet());
			p.splitHand();
			dealOne(cHand);
			nHand = p.getHand(p.getCurrentIndex() + 1);
			dealOne(nHand);
			nHand.setBet(cHand.getBet());
			showSplitHands(p);
			showSeparator();
			showPlayerHand(p, cHand, p.getCurrentIndex() + 1);
			quickEval();
		}
	}

	/* Blackjack behind-the-scenes methods */
	public void quickEval() {
		BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
		Hand cHand = p.getCurrentHand();
		if (p.hasSplit()) {
			showPlayerTurn(p, p.getCurrentIndex() + 1);
		} else {
			showPlayerTurn(p);
		}

		if (isHandBusted(cHand)) {
			showBusted(p);
			if (p.getNumberHands() > 1
					&& p.getCurrentIndex() < p.getNumberHands() - 1) {
				cHand = p.getNextHand();
				showPlayerHand(p, cHand, p.getCurrentIndex() + 1);
			} else {
				currentPlayer = getNextPlayer();
				if (currentPlayer == null) {
					endRound();
					return;
				}
			}
			showPlayerTurn(currentPlayer);
		} else if (isHandBlackjack(cHand)) {
			showBlackjack(p);
		}
		setIdleOutTimer();
	}

	public boolean isHandPair(Hand h) {
		if (h.getSize() > 2) {
			return false;
		}
		return h.get(0).getFace().equals(h.get(1).getFace());
	}

	public boolean isHandBlackjack(Hand h) {
		int sum = getCardSum(h);
		if (sum == 21 && h.getSize() == 2) {
			return true;
		}
		return false;
	}

	public boolean isHandBusted(Hand h) {
		int sum = getCardSum(h);
		if (sum > 21) {
			return true;
		}
		return false;
	}

	public int evaluateHand(Hand h) {
		Hand dHand = dealer.getCurrentHand();
		int sum = getCardSum(h), dsum = getCardSum(dHand);
		boolean pBlackjack = isHandBlackjack(h);
		boolean dBlackjack = isHandBlackjack(dHand);
		if (sum > 21) {
			return -1;
		} else if (sum == 21) {
			/* Different cases at 21 */
			if (pBlackjack && !dBlackjack) {
				return 2;
			} else if (pBlackjack && dBlackjack) {
				return 0;
			} else if (!pBlackjack && dBlackjack) {
				return -1;
			} else {
				if (dsum == 21) {
					return 0;
				} else {
					return 1;
				}
			}
		} else {
			/* Any case other than 21 */
			if (dsum > 21 || dsum < sum) {
				return 1;
			} else if (dsum == sum) {
				return 0;
			} else {
				return -1;
			}
		}
	}

	public int evaluateInsurance() {
		Hand dHand = dealer.getCurrentHand();
		if (isHandBlackjack(dHand)) {
			return 1;
		} else {
			return -1;
		}
	}

	public boolean dealerUpcardAce() {
		if (dealer.hasHands()) {
			Hand dHand = dealer.getCurrentHand();
			if (dHand.get(1).getFace().equals("A")) {
				return true;
			} else {
				return false;
			}
		}
		return false;
	}

	public boolean hasInsuranceBets() {
		return insuranceBets;
	}

	public void setInsuranceBets(boolean b) {
		insuranceBets = b;
	}

	public boolean needDealerHit() {
		for (int ctr = 0; ctr < getNumberPlayers(); ctr++) {
			BlackjackPlayer p = (BlackjackPlayer) getPlayer(ctr);
			for (int ctr2 = 0; ctr2 < p.getNumberHands(); ctr2++) {
				Hand h = p.getHand(ctr2);
				if (!isHandBusted(h) && !h.hasSurrendered()
						&& !isHandBlackjack(h)) {
					return true;
				}
			}
		}
		return false;
	}

	/* Channel output methods for Blackjack */
	@Override
	public void showTopPlayers(String param, int n){
    	int highIndex;
    	saveAllPlayers();
        try{
            ArrayList<String> nicks = new ArrayList<String>();
            ArrayList<Boolean> simples = new ArrayList<Boolean>();
            ArrayList<Integer> stacks = new ArrayList<Integer>();
            ArrayList<Integer> bankrupts = new ArrayList<Integer>();
            ArrayList<Integer> debts = new ArrayList<Integer>();
            ArrayList<Integer> bjrounds = new ArrayList<Integer>();
        	loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, simples);
        	ArrayList<Integer> test = new ArrayList<Integer>();
        	String list = "";
        	if (param.equals("cash")){
        		test = stacks;
        		list += Colors.BLACK+",08Top "+n+" Cash:";
        	} else if (param.equals("debt")){
        		test = debts;
        		list += Colors.BLACK+",08Top "+n+" Debt:";
        	} else if (param.equals("bankrupts")){
        		test = bankrupts;
        		list += Colors.BLACK+",08Top "+n+" Bankrupts:";
        	} else if (param.equals("net")){
        		for (int ctr=0; ctr<nicks.size(); ctr++){
        			test.add(stacks.get(ctr)-debts.get(ctr));
            	}
        		list += Colors.BLACK+",08Top "+n+" Net Cash:";
        	} else if (param.equals("rounds")){
        		test = bjrounds;
        		list += Colors.BLACK+",08Top "+n+" Blackjack Rounds:";
        	} else {
        		throw new IllegalArgumentException();
        	}
        	
        	for (int ctr=1; ctr<=n; ctr++){
            	highIndex = 0;
            	for (int ctr2=0; ctr2<nicks.size(); ctr2++){
            		if (test.get(ctr2) > test.get(highIndex)){
            			highIndex = ctr2;
            		}
            	}
            	if (param.equals("rounds") || param.equals("bankrupts")){
            		list += " #"+ctr+": "+Colors.WHITE+",04 "+nicks.get(highIndex)+" "+test.get(highIndex)+" "+Colors.BLACK+",08";
            	} else {
            		list += " #"+ctr+": "+Colors.WHITE+",04 "+nicks.get(highIndex)+" $"+String.format("%,d", test.get(highIndex))+" "+Colors.BLACK+",08";
            	}
            	nicks.remove(highIndex);
            	test.remove(highIndex);
            	if (nicks.isEmpty()){
            		break;
            	}
            }
            bot.sendMessage(channel, list);
        } catch (IOException e){
        	System.out.println("Error reading players.txt!");
        }
	}
	@Override
	public void showPlayerTurn(Player p) {
		if (isBetting()) {
			bot.sendMessage(channel,
					p.getNickStr() + "'s turn. Stack: $" + String.format("%,d", p.getCash())
							+ ". Enter an initial bet up to $" + String.format("%,d", p.getCash())
							+ ".");
		} else {
			bot.sendMessage(channel, "It's now " + p.getNickStr() + "'s turn.");
		}
	}

	public void showPlayerTurn(Player p, int index) {
		if (isBetting()) {
			bot.sendMessage(channel, p.getNickStr() + "-" + index
					+ "'s turn. Stack: $" + String.format("%,d", p.getCash())
					+ ". Enter an initial bet up to $" + String.format("%,d", p.getCash()) + ".");
		} else {
			bot.sendMessage(channel, "It's now " + p.getNickStr() + "-" + index
					+ "'s turn.");
		}
	}

	public void showPlayerHand(BlackjackPlayer p, Hand h, boolean nohole) {
		if (nohole) {
			bot.sendMessage(channel, p.getNickStr() + ": " + h.toString(0));
		} else if (p.isDealer() || deck.getNumberDecks() == 1) {
			bot.sendMessage(channel, p.getNickStr() + ": " + h.toString(1));
		} else {
			bot.sendMessage(channel, p.getNickStr() + ": " + h.toString(0));
		}
	}

	public void showPlayerHand(BlackjackPlayer p, Hand h) {
		if (p.isDealer() || deck.getNumberDecks() == 1) {
			bot.sendMessage(channel, p.getNickStr() + ": " + h.toString(1));
		} else {
			bot.sendMessage(channel, p.getNickStr() + ": " + h.toString(0));
		}
	}

	public void showPlayerHand(BlackjackPlayer p, Hand h, int handIndex) {
		if (p.isDealer() || deck.getNumberDecks() == 1) {
			bot.sendMessage(channel, p.getNickStr() + "-" + handIndex + ": "
					+ h.toString(1));
		} else {
			bot.sendMessage(channel, p.getNickStr() + "-" + handIndex + ": "
					+ h.toString(0));
		}
	}

	public void showPlayerHandWithBet(BlackjackPlayer p, Hand h, int handIndex) {
		if (p.isDealer() || deck.getNumberDecks() == 1) {
			bot.sendMessage(channel, p.getNickStr() + "-" + handIndex + ": "
					+ h.toString(1) + ", bet: $" + String.format("%,d", h.getBet()));
		} else {
			bot.sendMessage(channel, p.getNickStr() + "-" + handIndex + ": "
					+ h.toString(0) + ", bet: $" + String.format("%,d", h.getBet()));
		}
	}
	@Override
	public void showPlayerRounds(String nick){
    	int rounds = getPlayerRounds(nick);
    	if (rounds != Integer.MIN_VALUE){
        	bot.sendMessage(channel, nick+" has played "+rounds+" round(s) of "+getGameNameStr()+".");
        } else {
        	bot.sendMessage(channel, "No data found for "+nick+".");
        }
    }
	public void showDeckEmpty() {
		bot.sendMessage(channel,
				"The dealer's shoe is empty. Refilling the dealer's shoe...");
	}

	public void showProperBet(BlackjackPlayer p) {
		bot.sendMessage(channel, p.getNickStr() + " bets $" + String.format("%,d", p.getInitialBet())
				+ ". Stack: $" + String.format("%,d", p.getCash()));
	}

	public void showDoubleDown(BlackjackPlayer p, Hand h) {
		bot.sendMessage(channel, p.getNickStr()
				+ " has doubled down! The bet is now $" + String.format("%,d", h.getBet())
				+ ". Stack: $" + String.format("%,d", p.getCash()));
	}

	public void showSurrender(BlackjackPlayer p) {
		bot.sendMessage(
				channel,
				p.getNickStr()
						+ " has surrendered! Half the bet is returned and the rest forfeited. Stack: $"
						+ String.format("%,d", p.getCash()));
	}

	public void showInsure(BlackjackPlayer p, Hand h) {
		bot.sendMessage(channel, p.getNickStr()
				+ " has made an insurance bet of $" + String.format("%,d", h.getInsureBet())
				+ ". Stack: $" + String.format("%,d", p.getCash()));
	}

	public void showSplitHands(BlackjackPlayer p) {
		Hand h;
		bot.sendMessage(channel,
				p.getNickStr() + " has split the hand! " + p.getNickStr()
						+ "'s hands are now:");
		for (int ctr = 0; ctr < p.getNumberHands(); ctr++) {
			h = p.getHand(ctr);
			showPlayerHandWithBet(p, h, ctr + 1);
		}
		bot.sendMessage(channel, p.getNickStr() + "'s stack: $" + String.format("%,d", p.getCash()));
	}

	public void showShuffleShoe() {
		bot.sendMessage(channel, "The dealer's shoe has been shuffled.");
	}

	public void showReloadSettings() {
		bot.sendMessage(channel, "blackjack.ini has been reloaded.");
	}
	
	public void showUpdateSetting(String param) {
		bot.sendMessage(channel, param+" setting has been updated.");
	}

	public void showHouseStat(int n) {
		HouseStat hs = getHouseStat(n);
		if (hs != null) {
			bot.sendMessage(channel, hs.toString());
		} else {
			bot.sendMessage(channel, "No statistics found for " + n
					+ " deck(s).");
		}
	}

	public void showSeparator() {
		bot.sendMessage(
				channel,
				Colors.BOLD
						+ "------------------------------------------------------------------");
	}

	public void showDealingTable() {
		bot.sendMessage(channel, Colors.BOLD + Colors.DARK_GREEN + "Dealing..."
				+ Colors.NORMAL);
	}

	public void showBusted(BlackjackPlayer p) {
		bot.sendMessage(channel, p.getNickStr() + " has busted!");
	}

	public void showBlackjack(BlackjackPlayer p) {
		bot.sendMessage(channel, p.getNickStr() + " has blackjack!");
	}

	public void showTableHands() {
		BlackjackPlayer p;
		bot.sendMessage(channel, Colors.DARK_GREEN + Colors.BOLD + "Table:"
				+ Colors.NORMAL);
		for (int ctr = 0; ctr < getNumberPlayers(); ctr++) {
			p = (BlackjackPlayer) players.get(ctr);
			showPlayerHand(p, p.getCurrentHand());
		}
		showPlayerHand(dealer, dealer.getCurrentHand());
	}

	public void showInsuranceResults() {
		BlackjackPlayer p;
		Hand h, dHand = dealer.getCurrentHand();

		bot.sendMessage(channel, Colors.BOLD + Colors.DARK_GREEN
				+ "Insurance Results:" + Colors.NORMAL);

		if (isHandBlackjack(dHand)) {
			bot.sendMessage(channel, dealer.getNickStr() + " had blackjack.");
		} else {
			bot.sendMessage(channel, dealer.getNickStr()
					+ " did not have blackjack.");
		}

		for (int ctr = 0; ctr < getNumberPlayers(); ctr++) {
			p = (BlackjackPlayer) getPlayer(ctr);
			for (int ctr2 = 0; ctr2 < p.getNumberHands(); ctr2++) {
				h = p.getHand(ctr2);
				if (h.hasInsured()) {
					if (p.hasSplit()) {
						showPlayerInsuranceResult(p, h, ctr2 + 1);
					} else {
						showPlayerInsuranceResult(p, h);
					}
				}
			}
		}
	}

	public void showResults() {
		BlackjackPlayer p;
		Hand h;
		bot.sendMessage(channel, Colors.BOLD + Colors.DARK_GREEN + "Results:"
				+ Colors.NORMAL);
		showDealerResult();
		for (int ctr = 0; ctr < getNumberPlayers(); ctr++) {
			p = (BlackjackPlayer) getPlayer(ctr);
			for (int ctr2 = 0; ctr2 < p.getNumberHands(); ctr2++) {
				h = p.getHand(ctr2);
				if (p.hasSplit()) {
					showPlayerResult(p, h, ctr2 + 1);
				} else {
					showPlayerResult(p, h);
				}
			}
		}
	}

	public void showDealerResult() {
		Hand dHand = dealer.getCurrentHand();
		int sum = getCardSum(dHand);
		String outStr = dealer.getNickStr();
		if (sum > 21) {
			outStr += " has busted! (";
		} else if (isHandBlackjack(dHand)) {
			outStr += " has blackjack! (";
		} else {
			outStr += " has " + sum + ". (";
		}
		outStr += dHand.toString(0) + ")";
		bot.sendMessage(channel, outStr);
	}

	public void showPlayerResult(BlackjackPlayer p, Hand h) {
		String outStr = p.getNickStr();
		int result = evaluateHand(h);
		if (h.hasSurrendered()) {
			outStr += " surrendered. (";
		} else if (result == 2) {
			p.addCash(2 * h.getBet() + calcHalf(h.getBet()));
			house.addCash(-1 * (2 * h.getBet() + calcHalf(h.getBet())));
			outStr += " wins $" + String.format("%,d", (2 * h.getBet() + calcHalf(h.getBet())))
					+ ". (";
		} else if (result == 1) {
			p.addCash(2 * h.getBet());
			house.addCash(-2 * h.getBet());
			outStr += " wins $" + String.format("%,d", (2 * h.getBet())) + ". (";
		} else if (result == 0) {
			p.addCash(h.getBet());
			house.addCash(-1 * h.getBet());
			outStr += " pushes and his/her $" + String.format("%,d", h.getBet())
					+ " bet is returned. (";
		} else {
			outStr += " loses. (";
		}
		outStr += h.toString(0) + ") Stack: $" + String.format("%,d", p.getCash());
		bot.sendMessage(channel, outStr);
	}

	public void showPlayerResult(BlackjackPlayer p, Hand h, int index) {
		String outStr = p.getNickStr() + "-" + index;
		int result = evaluateHand(h);
		if (h.hasSurrendered()) {
			outStr += " surrendered. (";
		} else if (result == 2) {
			p.addCash(2 * h.getBet() + calcHalf(h.getBet()));
			house.addCash(-1 * (2 * h.getBet() + calcHalf(h.getBet())));
			outStr += " wins $" + String.format("%,d", (2 * h.getBet() + calcHalf(h.getBet())))
					+ ". (";
		} else if (result == 1) {
			p.addCash(2 * h.getBet());
			house.addCash(-2 * h.getBet());
			outStr += " wins $" + String.format("%,d", (2 * h.getBet())) + ". (";
		} else if (result == 0) {
			p.addCash(h.getBet());
			house.addCash(-1 * h.getBet());
			outStr += " pushes and his/her $" + String.format("%,d", h.getBet())
					+ " bet is returned. (";
		} else {
			outStr += " loses. (";
		}
		outStr += h.toString(0) + ") Stack: $" + String.format("%,d", p.getCash());
		bot.sendMessage(channel, outStr);
	}

	public void showPlayerInsuranceResult(BlackjackPlayer p, Hand h) {
		String outStr;
		int result = evaluateInsurance();
		if (result == 1) {
			p.addCash(3 * h.getInsureBet());
			house.addCash(-3 * h.getInsureBet());
			outStr = p.getNickStr() + " wins $" + String.format("%,d", 3 * h.getInsureBet()) + ".";
		} else {
			outStr = p.getNickStr() + " loses.";
		}
		outStr += " Stack: $" + String.format("%,d", p.getCash());
		bot.sendMessage(channel, outStr);
	}

	public void showPlayerInsuranceResult(BlackjackPlayer p, Hand h, int index) {
		String outStr;
		int result = evaluateInsurance();
		if (result == 1) {
			p.addCash(3 * h.getInsureBet());
			house.addCash(-3 * h.getInsureBet());
			outStr = p.getNickStr() + "-" + index + " wins $" + String.format("%,d", 3
					* h.getInsureBet()) + ".";
		} else {
			outStr = p.getNickStr() + "-" + index + " loses.";
		}
		outStr += " Stack: $" + String.format("%,d", p.getCash());
		bot.sendMessage(channel, outStr);
	}

	/* Player/User output methods to simplify messaging/noticing */
	@Override
	public void infoNumCards(User user){
        bot.sendNotice(user, deck.getNumberCards()+" cards left in the dealer's shoe.");
    }
	public void infoNotPair(BlackjackPlayer p) {
		bot.sendNotice(p.getUser(),
				"Your hand cannot be split. The cards do not have matching faces.");
	}

	public void infoInsureBetTooHigh(BlackjackPlayer p) {
		bot.sendNotice(p.getUser(),
				"Maximum insurance bet is $" + String.format("%,d", calcHalf(p.getInitialBet()))
						+ ". Try again.");
	}

	public void infoNotDoubleDown(BlackjackPlayer p) {
		bot.sendNotice(p.getUser(), "You can only double down before hitting!");
	}

	public void infoNotSurrender(BlackjackPlayer p) {
		bot.sendNotice(p.getUser(), "You can only surrender before hitting!");
	}

	public void infoNotInsure(BlackjackPlayer p) {
		bot.sendNotice(p.getUser(),
				"The dealer's upcard is not an ace. You cannot make an insurance bet.");
	}

	public void infoAlreadyInsured(BlackjackPlayer p) {
		bot.sendNotice(p.getUser(), "You have already made an insurance bet.");
	}

	public void infoPlayerHand(BlackjackPlayer p, Hand h) {
		if (p.isSimple()) {
			bot.sendNotice(p.getUser(), "Your current hand is " + h.toString(0)
					+ ".");
		} else {
			bot.sendMessage(p.getUser(),
					"Your current hand is " + h.toString(0) + ".");
		}
	}

	public void infoPlayerSum(BlackjackPlayer p, Hand h) {
		if (p.isSimple()) {
			bot.sendNotice(p.getUser(), "Current sum is " + getCardSum(h) + ".");
		} else {
			bot.sendMessage(p.getUser(), "Current sum is " + getCardSum(h)
					+ ".");
		}
	}

	public void infoPlayerBet(BlackjackPlayer p, Hand h) {
		String outStr = "This hand has a bet $" + String.format("%,d", h.getBet());
		if (h.hasInsured()) {
			outStr += " with an insurance bet of $" + String.format("%,d", h.getInsureBet());
		}
		outStr += ".";
		if (p.isSimple()) {
			bot.sendNotice(p.getUser(), outStr);
		} else {
			bot.sendMessage(p.getUser(), outStr);
		}
	}

	public void infoPlayerBankrupt(User user) {
		Player p = findPlayer(user);
		if (p.isSimple()) {
			bot.sendNotice(p.getUser(),
					"You've lost all your money. Please wait " + respawnTime
							/ 60000 + " minute(s) for a loan.");
		} else {
			bot.sendMessage(p.getUser(),
					"You've lost all your money. Please wait " + respawnTime
							/ 60000 + " minute(s) for a loan.");
		}
	}

	/* Formatted strings */
	@Override
	public String getGameNameStr() {
		return Colors.BOLD + gameName + Colors.NORMAL;
	}
	@Override
	public String getGameHelpStr(){
		return "For help on how to play "+ getGameNameStr() + ", please visit an online resource. "
				+ "For game commands, type .gcommands. For house rules, type .grules.";
	}
	@Override
	public String getGameRulesStr() {
		return "Dealer stands on soft 17. The dealer's shoe has "+ deck.getNumberDecks()
				+ " deck(s) of cards. Cards are reshuffled when the shoe is depleted. "
				+ "Regular wins are paid out at 1:1 and blackjacks are paid out at 3:2. "
				+ "Insurance wins are paid out at 2:1";
	}
	@Override
	public String getGameCommandStr() {
		return "start (go), join (j), leave (quit, l, q), bet (b), hit (h), stay (sit, stand), doubledown (dd), "
				+ "surrender (surr), insure, split, table, turn, sum, hand, allhands, cash, netcash (net), "
				+ "debt, bankrupts, rounds, numdecks (ndecks), numcards (ncards), numdiscards (ndiscards), "
				+ "hilo (hc), zen (zc), red7 (rc), simple, players, waitlist, blacklist, top5, "
				+ "gamehelp (ghelp), gamerules (grules), gamecommands (gcommands)";
	}
}