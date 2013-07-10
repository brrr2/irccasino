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

import java.io.*;
import java.util.*;

import org.pircbotx.*;
import org.pircbotx.hooks.events.*;

public class TexasPoker extends CardGame{
	public static class IdleOutTask extends TimerTask {
		TexasPokerPlayer player;
		TexasPoker game;

		public IdleOutTask(TexasPokerPlayer p, TexasPoker g) {
			player = p;
			game = g;
		}

		@Override
		public void run() {
			if (player == game.getCurrentPlayer()) {
				player.setQuit(true);
				game.bot.sendMessage(game.channel, player.getNickStr()
						+ " has wasted precious time and idled out.");
				game.fold();
			}
		}
	}
	public static class PokerPot {
		ArrayList<TexasPokerPlayer> players;
		int pot;
		
		public PokerPot(){
			pot = 0;
			players = new ArrayList<TexasPokerPlayer>();
		}
		
		public int getPot(){
			return pot;
		}
		public void addPot(int amount){
			pot += amount;
		}
		public void addPlayer(TexasPokerPlayer p){
			players.add(p);
		}
		public void removePlayer(TexasPokerPlayer p){
			players.remove(p);
		}
		public TexasPokerPlayer getPlayer(int c){
			return players.get(c);
		}
		public boolean hasPlayer(TexasPokerPlayer p){
			return players.contains(p);
		}
		public int getNumberPlayers(){
			return players.size();
		}
	}
	
	private int stage, currentBet, minRaise, minBet;
	private ArrayList<PokerPot> pots;
	private PokerPot currentPot;
	private TexasPokerPlayer dealer, smallBlind, bigBlind, topBettor;
	private Hand community;
	
	public TexasPoker(PircBotX parent, Channel gameChannel, char c){
		super(parent, gameChannel, c);
		gameName = "Texas Hold'em Poker";
		deck = new CardDeck();
		deck.shuffleCards();
		loadSettings();
		pots = new ArrayList<PokerPot>();
		community = new Hand();
		stage = 0;
		currentBet = 0;
		currentPot = null;
		dealer = null;
		smallBlind = null;
		bigBlind = null;
		topBettor = null;
	}
	
	@Override
	public void onPart(PartEvent<PircBotX> event) {
		String nick = event.getUser().getNick();
		if (isJoined(nick) || isWaitlisted(nick)){
			leave(nick);
		}
	}

	@Override
	public void onQuit(QuitEvent<PircBotX> event) {
		String nick = event.getUser().getNick();
		if (isJoined(nick) || isWaitlisted(nick)){
			leave(nick);
		}
	}
	
    @Override
    public void onNickChange(NickChangeEvent<PircBotX> e){
    	String oldNick = e.getOldNick();
    	String newNick = e.getNewNick();
    	String hostmask = e.getUser().getHostmask();
    	if (isJoined(oldNick) || isWaitlisted(oldNick)){
    		infoNickChange(newNick);
    		if (isJoined(oldNick)){
		    	leave(oldNick);
	    	} else if(isWaitlisted(oldNick)){
				removeWaitlisted(oldNick);
	    	}
    		join(newNick, hostmask);
    	}
    }
	
	@Override
	public void onMessage(MessageEvent<PircBotX> event){
		String origMsg = event.getMessage();
		String msg = origMsg.toLowerCase();

		if (msg.charAt(0) == getCommandChar() && msg.length() > 1) {
			User user = event.getUser();
			String nick = user.getNick();
			String hostmask = user.getHostmask();
			msg = msg.substring(1);
			origMsg = origMsg.substring(1);
			
			/* Parsing commands from the channel */
			if (msg.equals("join") || msg.equals("j")) {
				join(nick, hostmask);
			} else if (msg.equals("leave") || msg.equals("quit")
					|| msg.equals("l") || msg.equals("q")) {
				leave(nick);
			} else if (msg.equals("start") || msg.equals("go")) {
				if (!isJoined(nick)) {
					infoNotJoined(nick);
				} else if (isInProgress()) {
					infoRoundStarted(nick);
				} else if (getNumberJoined() < 2) {
					showNoPlayers();
				} else {
					showStartRound();
					setButton();
					showTablePlayers();
					setInProgress(true);
					setStartRoundTimer();
				}
			} else if (msg.startsWith("bet ") || msg.startsWith("b ")
					|| msg.equals("bet") || msg.equals("b")) {
				if (!isJoined(nick)){
					infoNotJoined(nick);
				} else if (!isInProgress()) {
					infoNotStarted(nick);
				} else if (findJoined(nick) != currentPlayer){
					infoNotTurn(nick);
				} else {
					try {
						try {
							int amount = parseNumberParam(msg);
							bet(amount);
						} catch (NumberFormatException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(nick);
					}
				}
			} else if (msg.equals("check") || msg.equals("c")
					|| msg.equals("call")) {
				if (!isJoined(nick)){
					infoNotJoined(nick);
				} else if (!isInProgress()) {
					infoNotStarted(nick);
				} else if (findJoined(nick) != currentPlayer){
					infoNotTurn(nick);
				} else {
					checkCall();
				}
			} else if (msg.equals("fold") || msg.equals("f")) {
				if (!isJoined(nick)){
					infoNotJoined(nick);
				} else if (!isInProgress()) {
					infoNotStarted(nick);
				} else if (findJoined(nick) != currentPlayer){
					infoNotTurn(nick);
				} else {
					fold();
				}
			} else if (msg.startsWith("raise ") || msg.startsWith("r ")
					|| msg.equals("raise") || msg.equals("r")) {
				if (!isJoined(nick)){
					infoNotJoined(nick);
				} else if (!isInProgress()) {
					infoNotStarted(nick);
				} else if (findJoined(nick) != currentPlayer){
					infoNotTurn(nick);
				} else {
					try {
						try {
							int amount = parseNumberParam(msg);
							raise(amount);
						} catch (NumberFormatException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(nick);
					}
				}
			} else if (msg.equals("allin") || msg.equals("a")){
				if (!isJoined(nick)){
					infoNotJoined(nick);
				} else if (!isInProgress()) {
					infoNotStarted(nick);
				} else if (findJoined(nick) != currentPlayer){
					infoNotTurn(nick);
				} else {
					bet(Integer.MAX_VALUE);
				}
			} else if (msg.equals("table")) {

			} else if (msg.equals("hand")) {
				TexasPokerPlayer p = (TexasPokerPlayer) findJoined(nick);
				infoPlayerHand(p, p.getHand());
			} else if (msg.equals("turn")) {
				if (!isJoined(nick)) {
					infoNotJoined(nick);
				} else if (!isInProgress()) {
					infoNotStarted(nick);
				} else {
					showTurn(currentPlayer);
				}
			} else if (msg.equals("simple")) {
				if (!isJoined(nick)) {
					infoNotJoined(nick);
				} else {
					togglePlayerSimple(nick);
				}
			} else if (msg.equals("stats")){
				showGameStats();
			} else if (msg.startsWith("cash ") || msg.equals("cash")) {
				try {
					String param = parseStringParam(origMsg);
					showPlayerCash(param);
				} catch (NoSuchElementException e) {
					showPlayerCash(nick);
				}
			} else if (msg.startsWith("netcash ") || msg.equals("netcash")
					|| msg.startsWith("net ") || msg.equals("net")) {
				try {
					String param = parseStringParam(origMsg);
					showPlayerNetCash(param);
				} catch (NoSuchElementException e) {
					showPlayerNetCash(nick);
				}
			} else if (msg.startsWith("debt ") || msg.equals("debt")) {
				try {
					String param = parseStringParam(origMsg);
					showPlayerDebt(param);
				} catch (NoSuchElementException e) {
					showPlayerDebt(nick);
				}
			} else if (msg.startsWith("bankrupts ")
					|| msg.equals("bankrupts")) {
				try {
					String param = parseStringParam(origMsg);
					showPlayerBankrupts(param);
				} catch (NoSuchElementException e) {
					showPlayerBankrupts(nick);
				}
			} else if (msg.startsWith("rounds ") || msg.equals("rounds")) {
				try {
					String param = parseStringParam(origMsg);
					showPlayerRounds(param);
				} catch (NoSuchElementException e) {
					showPlayerRounds(nick);
				}
			} else if (msg.startsWith("paydebt ") || msg.equals("paydebt") ) {
				if (!isJoined(nick)) {
					infoNotJoined(nick);
				} else if (isInProgress()) {
					infoWaitRoundEnd(nick);
				} else {
					try {
						try {
							int amount = parseNumberParam(msg);
							payPlayerDebt(nick, amount);
						} catch (NumberFormatException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(nick);
					}
				}
			} else if (msg.equals("players")) {
				showPlayers();
			} else if (msg.equals("waitlist")) {
				showWaitlist();
			} else if (msg.equals("blacklist")) {
				showBlacklist();
			} else if (msg.startsWith("top5 ") || msg.equals("top5") || 
					msg.startsWith("top10 ") || msg.equals("top10")) {
				if (isInProgress()) {
					infoWaitRoundEnd(nick);
				} else {
					try {
						try {
							String param = parseStringParam(msg).toLowerCase();
							if (msg.startsWith("top5")){
								showTopPlayers(param, 5);
							} else if (msg.startsWith("top10")){
								showTopPlayers(param, 10);
							}
						} catch (IllegalArgumentException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						if (msg.startsWith("top5")){
							showTopPlayers("cash", 5);
						} else if (msg.startsWith("top10")){
							showTopPlayers("cash", 10);
						}
					}
				}
			} else if (msg.equals("gamerules") || msg.equals("grules")) {
				infoGameRules(nick);
			} else if (msg.equals("gamehelp") || msg.equals("ghelp")) {
				infoGameHelp(nick);
			} else if (msg.equals("gamecommands") || msg.equals("gcommands")) {
				infoGameCommands(nick);
			} else if (msg.equals("currentgame") || msg.equals("game")) {
				showGameName();
			/* Op commands */
			} else if (msg.startsWith("cards ") || msg.startsWith("discards ") || 
					msg.equals("cards") || msg.equals("discards")) {
				if (isOpCommandAllowed(user, nick)){
					try {
						try {
							int num = parseNumberParam(msg);
							if (msg.startsWith("cards ")
									&& deck.getNumberCards() > 0) {
								infoDeckCards(nick, 'c', num);
							} else if (msg.startsWith("discards ")
									&& deck.getNumberDiscards() > 0) {
								infoDeckCards(nick, 'd', num);
							} else {
								bot.sendNotice(nick, "Empty!");
							}
						} catch (NumberFormatException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(nick);
					}
				}
			} else if (msg.equals("reload")) {
				if (isOpCommandAllowed(user, nick)){
					loadSettings();
					showReloadSettings();
				}
			} else if (msg.startsWith("set ") || msg.equals("set")) {
				if (isOpCommandAllowed(user, nick)){
					try {
						try {
							String[] iniParams = parseIniParams(msg);
							setSetting(iniParams);
							showUpdateSetting(iniParams[0]);
						} catch (IllegalArgumentException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(nick);
					}
				}
			} else if (msg.startsWith("get ") || msg.equals("get")) {
				if (isOpCommandAllowed(user, nick)){
					try {
						try {
							String param = parseStringParam(msg);
							showSetting(param,getSetting(param));
						} catch (IllegalArgumentException e) {
							infoBadParameter(nick);
						}
					} catch (NoSuchElementException e) {
						infoNoParameter(nick);
					}
				}
			}
		}
	}

	public void setMinBet(int value){
    	minBet = value;
    }
    public int getMinBet(){
    	return minBet;
    }
	
	@Override
	public void startRound() {
		if (getNumberJoined() < 2){
			endRound();
		} else {
			dealTable();
			setBlindBets();
			currentPlayer = getPlayerAfter(bigBlind);
			showTurn(currentPlayer);
			setIdleOutTimer();
		}
	}

	@Override
	public void continueRound() {
		currentPlayer = getPlayerAfter(currentPlayer);
		TexasPokerPlayer p = (TexasPokerPlayer) currentPlayer;
		while(p.hasFolded()){
			currentPlayer = getPlayerAfter(currentPlayer);
			p = (TexasPokerPlayer) currentPlayer;
		}
		if (currentPlayer == topBettor || getNumberNotFolded() == 1) {
			addBetsToPot();
			stage++;
			
			if (stage == 4){
				endRound();
			} else {
				if (stage != 1){
					burnOne();
				}
				dealCommunity();
				showCommunityCards();
				topBettor = null;
				currentPlayer = dealer;
				continueRound();
			}
		} else {
			showTurn(currentPlayer);
			setIdleOutTimer();
		}
	}

	@Override
	public void endRound() {
		TexasPokerPlayer p;
		setInProgress(false);
		if (currentPot != null){
			pots.add(currentPot);
		}
		
		if (getNumberJoined() > 0) {
			//Determine pot winners
			for (int ctr = 0; ctr < pots.size(); ctr++){
				currentPot = pots.get(ctr);
				//p = findWinners(currentPot);
				p = currentPot.getPlayer(0);
				bot.sendMessage(channel, p.getNickStr()+" wins pot "+ctr+": $"+currentPot.getPot());
				p.addCash(currentPot.getPot());
			}
				
			// Clean-up tasks
			for (int ctr = 0; ctr < getNumberJoined(); ctr++){
				p = (TexasPokerPlayer) getJoined(ctr);
				showPlayerHand(p, p.getHand());
				p.incrementRounds();

				if (p.getCash() == 0) {
					p.incrementBankrupts();
					blacklist.add(p);
					infoPlayerBankrupt(p.getNick());
					bot.sendMessage(channel, p.getNickStr()	+ " has gone bankrupt. " +
							"S/He has been kicked to the curb.");
						removeJoined(p.getNick());
						showLeave(p);
						setRespawnTimer(p);
						ctr--;
				}
				if (p.hasQuit() && isJoined(p)) {
					removeJoined(p.getNick());
					showLeave(p);
					ctr--;
				}
				savePlayerData(p);
				resetPlayer(p);
			}
		} else {
			showNoPlayers();
		}
		
		resetGame();
		showEndRound();
		showSeparator();
		mergeWaitlist();
	}

	@Override
	public void endGame() {
		cancelStartRoundTimer();
		cancelIdleOutTimer();
		cancelRespawnTimers();
		saveAllPlayers();
		saveSettings();
		joined.clear();
		waitlist.clear();
		blacklist.clear();
		deck = null;
		pots.clear();
		currentPlayer = null;
		dealer = null;
		smallBlind = null;
		bigBlind = null;
		topBettor = null;
	}

	@Override
	public void resetGame() {
		discardCommunity();
		stage = 0;
		currentBet = 0;
		currentPot = null;
		minRaise = minBet;
		pots.clear();
		topBettor = null;
		deck.refillDeck();
	}
	public void resetPlayer(TexasPokerPlayer p) {
		discardPlayerHand(p);
		p.setFold(false);
		p.setQuit(false);
	}
	@Override
	public void leave(String nick) {
		if (isJoined(nick)){
			TexasPokerPlayer p = (TexasPokerPlayer) findJoined(nick);
			if (isInProgress()) {
				p.setQuit(true);
				if (p == currentPlayer){
					fold();
				} else {
					p.setFold(true);
				}
			} else {
				savePlayerData(p);
				removeJoined(p);
				showLeave(p);
			}
		} else if (isWaitlisted(nick)) {
			infoLeaveWaitlist(nick);
			removeWaitlisted(nick);
		} else {
			infoNotJoined(nick);
		}
	}

	@Override
	public void setIdleOutTimer() {
		idleOutTimer = new Timer();
		idleOutTimer.schedule(new IdleOutTask((TexasPokerPlayer) currentPlayer,	this), idleOutTime*1000);
	}

	@Override
	public void cancelIdleOutTimer() {
		if (idleOutTimer != null) {
			idleOutTimer.cancel();
			idleOutTimer = null;
		}
	}

	public void setButton(){
		if (dealer == null){
			dealer = (TexasPokerPlayer) getJoined(0);
		} else {
			dealer = (TexasPokerPlayer) getPlayerAfter(dealer);
		}
		if (getNumberJoined() == 2){
			smallBlind = dealer;
		} else {
			smallBlind = (TexasPokerPlayer) getPlayerAfter(dealer);
		}
		bigBlind = (TexasPokerPlayer) getPlayerAfter(smallBlind);
	}
	public void setBlindBets(){
		currentBet = Math.min(minRaise, smallBlind.getCash());
		smallBlind.setBet(currentBet);
		currentBet = Math.min(currentBet+minRaise, smallBlind.getCash());
		bigBlind.setBet(currentBet);
	}
	
	@Override
	public void setSetting(String[] params) {
		String setting = params[0];
		String value = params[1];
		if (setting.equals("idle")) {
			setIdleOutTime(Integer.parseInt(value));
		} else if (setting.equals("cash")) {
			setNewCash(Integer.parseInt(value));
		} else if (setting.equals("respawn")) {
			setRespawnTime(Integer.parseInt(value));
		} else if (setting.equals("minbet")){
			setMinBet(Integer.parseInt(value));
		} else {
			throw new IllegalArgumentException();
		}
		saveSettings();
	}

	@Override
	public String getSetting(String param) {
		if (param.equals("idle")) {
			return getIdleOutTime()+"";
		} else if (param.equals("cash")) {
			return getNewCash()+"";
		} else if (param.equals("respawn")) {
			return getRespawnTime()+"";
		} else if (param.equals("minbet")){
			return getMinBet()+"";
		} else {
			throw new IllegalArgumentException();
		}
	}

	@Override
	public void loadSettings() {
		try {
			BufferedReader f = new BufferedReader(new FileReader("texaspoker.ini"));
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
				if (name.equals("idle")) {
					idleOutTime = Integer.parseInt(value);
				} else if (name.equals("cash")) {
					newcash = Integer.parseInt(value);
				} else if (name.equals("respawn")) {
					respawnTime = Integer.parseInt(value);
				} else if (name.equals("minbet")) {
					minBet = Integer.parseInt(value);
					minRaise = minBet;
				}
			}
			f.close();
		} catch (IOException e) {
			/* load defaults if texaspoker.ini is not found */
			System.out.println("texaspoker.ini not found! Creating new texaspoker.ini...");
			newcash = 1000;
			idleOutTime = 60;
			respawnTime = 600;
			minBet = 5;
			minRaise = minBet;
			saveSettings();
		}
	}

	@Override
	public void saveSettings() {
		try {
			PrintWriter out = new PrintWriter(new BufferedWriter(
					new FileWriter("texaspoker.ini")));
			out.println("#Settings");
			out.println("#Number of seconds before a player idles out");
			out.println("idle=" + idleOutTime);
			out.println("#Initial amount given to new and bankrupt players");
			out.println("cash=" + newcash);
			out.println("#Number of seconds before a bankrupt player is allowed to join again");
			out.println("respawn=" + respawnTime);
			out.println("#Minimum betting increment");
			out.println("minbet=" + minBet);
			out.close();
		} catch (IOException f) {
			System.out.println("Error creating texaspoker.ini!");
		}
	}
	
	public boolean isOpCommandAllowed(User user, String nick){
		if (isInProgress()) {
			infoWaitRoundEnd(nick);
			return false;
		} else if (!channel.isOp(user)) {
			infoOpsOnly(nick);
			return false;
		}
		return true;
	}

	public void discardPlayerHand(TexasPokerPlayer p) {
		if (p.hasHand()) {
			deck.addToDiscard(p.getHand().getAllCards());
			p.resetHand();
		}
	}
	public void discardCommunity(){
		deck.addToDiscard(community.getAllCards());
		community.clear();
	}

	@Override
	public int getTotalPlayers() {
		try {
	    	ArrayList<String> nicks = new ArrayList<String>();
	        ArrayList<Integer> stacks = new ArrayList<Integer>();
	        ArrayList<Integer> bankrupts = new ArrayList<Integer>();
	        ArrayList<Integer> debts = new ArrayList<Integer>();
	        ArrayList<Integer> bjrounds = new ArrayList<Integer>();
	        ArrayList<Integer> tprounds = new ArrayList<Integer>();
	        ArrayList<Boolean> simples = new ArrayList<Boolean>();
	    	loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
	    	int total = 0, numLines = nicks.size();
        	for (int ctr = 0; ctr < numLines; ctr++){
        		if (tprounds.get(ctr) > 0){
        			total++;
        		}
        	}
        	return total;
    	} catch (IOException e){
		 	System.out.println("Error reading players.txt!");
		 	return -1;
    	}
	}

	@Override
	public void loadPlayerData(Player p) {
		try {
			boolean found = false;
			ArrayList<String> nicks = new ArrayList<String>();
			ArrayList<Integer> stacks = new ArrayList<Integer>();
			ArrayList<Integer> bankrupts = new ArrayList<Integer>();
			ArrayList<Integer> debts = new ArrayList<Integer>();
			ArrayList<Integer> bjrounds = new ArrayList<Integer>();
			ArrayList<Integer> tprounds = new ArrayList<Integer>();
			ArrayList<Boolean> simples = new ArrayList<Boolean>();
			loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
			int numLines = nicks.size();
			for (int ctr = 0; ctr < numLines; ctr++) {
				if (p.getNick().toLowerCase().equals(nicks.get(ctr).toLowerCase())) {
					if (stacks.get(ctr) <= 0) {
						p.setCash(getNewCash());
					} else {
						p.setCash(stacks.get(ctr));
					}
					p.setDebt(debts.get(ctr));
					p.setBankrupts(bankrupts.get(ctr));
					p.setRounds(tprounds.get(ctr));
					p.setSimple(simples.get(ctr));
					found = true;
					break;
				}
			}
			if (!found) {
				p.setCash(getNewCash());
				p.setDebt(0);
				p.setBankrupts(0);
				p.setRounds(0);
				p.setSimple(true);
				infoNewPlayer(p.getNick());
			}
		} catch (IOException e) {
			System.out.println("Error reading players.txt!");
		}
	}

	@Override
	public void savePlayerData(Player p) {
		boolean found = false;
		ArrayList<String> nicks = new ArrayList<String>();
		ArrayList<Integer> stacks = new ArrayList<Integer>();
		ArrayList<Integer> debts = new ArrayList<Integer>();
		ArrayList<Integer> bankrupts = new ArrayList<Integer>();
		ArrayList<Integer> bjrounds = new ArrayList<Integer>();
		ArrayList<Integer> tprounds = new ArrayList<Integer>();
		ArrayList<Boolean> simples = new ArrayList<Boolean>();
		int numLines;
		try {
			loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
			numLines = nicks.size();
			for (int ctr = 0; ctr < numLines; ctr++) {
				if (p.getNick().toLowerCase().equals(nicks.get(ctr).toLowerCase())) {
					stacks.set(ctr, p.getCash());
					debts.set(ctr, p.getDebt());
					bankrupts.set(ctr, p.getBankrupts());
					tprounds.set(ctr, p.getRounds());
					simples.set(ctr, p.isSimple());
					found = true;
				}
			}
			if (!found) {
				nicks.add(p.getNick());
				stacks.add(p.getCash());
				debts.add(p.getDebt());
				bankrupts.add(p.getBankrupts());
				bjrounds.add(0);
				tprounds.add(p.getRounds());
				simples.add(p.isSimple());
			}
		} catch (IOException e) {
			System.out.println("Error reading players.txt!");
		}

		try {
			savePlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
		} catch (IOException e) {
			System.out.println("Error writing to players.txt!");
		}
	}

	@Override
	public void addPlayer(String nick, String hostmask) {
		addPlayer(new TexasPokerPlayer(nick, hostmask, false));
	}

	@Override
	public void addWaitlistPlayer(String nick, String hostmask) {
		Player p = new TexasPokerPlayer(nick, hostmask, false);
		waitlist.add(p);
		infoJoinWaitlist(p.getNick());
	}
	public Player getPlayerAfter(Player p){
		return getJoined((getJoinedIndex(p)+1) % getNumberJoined());
	}

	public int getNumberNotFolded(){
		TexasPokerPlayer p;
		int numberNotFolded = 0;
		for (int ctr = 0; ctr < getNumberJoined(); ctr++){
			p = (TexasPokerPlayer) getJoined(ctr);
			if (p.hasFolded()){
				numberNotFolded++;
			}
		}
		return numberNotFolded;
	}
	public int getNumberCanBet(){
		TexasPokerPlayer p;
		int numberCanBet = 0;
		for (int ctr = 0; ctr < getNumberJoined(); ctr++){
			p = (TexasPokerPlayer) getJoined(ctr);
			if (p.getCash() - p.getBet() > 0){
				numberCanBet++;
			}
		}
		return numberCanBet;
	}
	
	public void burnOne(){
		deck.addToDiscard(deck.takeCard());
	}
	public void dealOne(Hand h) {
		h.add(deck.takeCard());
	}
	public void dealHand(TexasPokerPlayer p) {
		Hand h = p.getHand();
		for (int ctr2 = 0; ctr2 < 2; ctr2++) {
			h.add(deck.takeCard());
		}
	}
	public void dealTable() {
		TexasPokerPlayer p;
		for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
			p = (TexasPokerPlayer) getJoined(ctr);
			dealHand(p);
			infoPlayerHand(p, p.getHand());
		}
	}
	public void dealCommunity(){
		if (stage == 1) {
			for (int ctr = 1; ctr <= 3; ctr++){
				community.add(deck.takeCard());
			}
		} else {
			community.add(deck.takeCard());
		}
	}
	
	public void bet (int amount) {
		cancelIdleOutTimer();
		TexasPokerPlayer p = (TexasPokerPlayer) currentPlayer;
		int total;
		if (amount == Integer.MAX_VALUE){
			total = p.getCash();
		} else {
			total = amount;
		}
		
		if (total == p.getCash()){
			p.setBet(total);
			if (currentBet < total){
				currentBet = total;
				topBettor = p;
			} else if (topBettor == null){
				currentBet = total;
				topBettor = p;
			}
			bot.sendNotice(p.getNick(), p.getNickStr()+" has gone all in!");
			showBet(p);
			continueRound();
		} else if (total > p.getCash()) {
			bot.sendNotice(p.getNick(), "Not enough cash.");
			setIdleOutTimer();
		} else if (total < currentBet) {
			bot.sendNotice(p.getNick(), "Bet too low. Current bet is $"+formatNumber(currentBet)+".");
			setIdleOutTimer();
		} else if (total == currentBet){
			p.setBet(total);
			if (topBettor == null){
				topBettor = p;
			}
			if (total == 0){
				bot.sendMessage(channel, p.getNickStr()+" has checked. Stack: $"+formatNumber(p.getCash()-p.getBet()));
			} else {
				bot.sendMessage(channel, p.getNickStr()+" has called. Stack: $"+formatNumber(p.getCash()-p.getBet()));
			}
			showBet(p);
			continueRound();
		} else if ((total-currentBet) < minRaise){
			bot.sendNotice(p.getNick(), "Minimum raise is $"+formatNumber(minRaise)+".");
			setIdleOutTimer();
		} else {
			p.setBet(total);
			currentBet = total;
			topBettor = p;
			bot.sendMessage(channel, p.getNickStr()+" has raised to $"+formatNumber(total)+".");
			showBet(p);
			continueRound();
		}
	}
	public void raise (int amount) {
		cancelIdleOutTimer();
		TexasPokerPlayer p = (TexasPokerPlayer) currentPlayer;
		int total = amount + currentBet;
		
		if (total > p.getCash()) {
			bot.sendNotice(p.getNick(), "Not enough cash");
			setIdleOutTimer();
		} else if (amount < minRaise) {
			bot.sendNotice(p.getNick(), "Minimum raise is "+formatNumber(minRaise));
			setIdleOutTimer();
		} else {
			p.setBet(total);			
			currentBet = total;
			topBettor = p;
			showBet(p);
			continueRound();
		}
	}
	public void checkCall(){
		cancelIdleOutTimer();
		TexasPokerPlayer p = (TexasPokerPlayer) currentPlayer;
		int total = Math.min(p.getCash(), currentBet);
		p.setBet(total);
		if (topBettor == null){
			topBettor = p;
		}
		if (total == 0){
			bot.sendMessage(channel, p.getNickStr()+" has checked. Stack: $"+formatNumber(p.getCash()-p.getBet()));
		} else {
			bot.sendMessage(channel, p.getNickStr()+" has called. Stack: $"+formatNumber(p.getCash()-p.getBet()));
		}
		showBet(p);
		continueRound();
	}
	public void fold(){
		cancelIdleOutTimer();
		TexasPokerPlayer p = (TexasPokerPlayer) currentPlayer;
		p.setFold(true);
		showFold(p);
		//Remove this player from any existing pots
		for (int ctr = 0; ctr < pots.size(); ctr++){
			PokerPot cPot = pots.get(ctr);
			if (cPot.hasPlayer(p)){
				cPot.removePlayer(p);
			}
		}
		continueRound();
	}
	
	public void addBetsToPot(){
		//bot.sendMessage(channel, "Adding bets to pot.");
		TexasPokerPlayer p;
		
		while(currentBet != 0){
			int lowBet = currentBet;
			if (currentPot == null){
				currentPot = new PokerPot();
			}
			for (int ctr = 0; ctr < getNumberJoined(); ctr++){
				p = (TexasPokerPlayer) getJoined(ctr);
				if (p.getBet() < lowBet && p.getBet() != 0){
					lowBet = p.getBet();
				}
			}
			for (int ctr = 0; ctr < getNumberJoined(); ctr++){
				p = (TexasPokerPlayer) getJoined(ctr);
				if (p.getBet() != 0){
					currentPot.addPot(lowBet);
					p.addCash(-1*lowBet);
					p.addBet(-1*lowBet);
				}
					
				if (!p.hasFolded() && !currentPot.hasPlayer(p)){
					currentPot.addPlayer(p);
				}
			}
			currentBet -= lowBet;
			if (currentBet != 0){
				pots.add(currentPot);
				currentPot = null;
			}
		}
		if (currentPot != null && currentPot.getNumberPlayers() == 1){
			currentPot.getPlayer(0).setCash(currentPot.getPot());
			currentPot = null;
		}
	}
	//public TexasPokerPlayer findWinner(PokerPot pot){
		
	//}
	
	/*
	 * A smattering of methods to check for various poker card combinations.
	 * Methods require that hand parameters be sorted in ascending order.
	 */
	public boolean hasPair(Hand h){
		Card a,b;
		for (int ctr = 0; ctr < h.getSize()-1; ctr++){
			a = h.get(ctr);
			for (int ctr2 = ctr+1; ctr2 < h.getSize(); ctr2++){
				b = h.get(ctr2);
				if (a.getFace() == b.getFace()){
					return true;
				}
			}
		}
		return false;
	}
	public boolean hasTwoPair(Hand h){
		Card a,b;
		for (int ctr = 0; ctr < h.getSize()-1; ctr++){
			a = h.get(ctr);
			for (int ctr2 = ctr+1; ctr2 < h.getSize(); ctr2++){
				b = h.get(ctr2);
				if (a.getFace() == b.getFace()){
					h.remove(a);
					h.remove(b);
					return hasPair(h);
				}
			}
		}
		return false;
	}
	public boolean hasThreeOfAKind(Hand h){
		Card a,b,c;
		for (int ctr = 0; ctr < h.getSize()-2; ctr++){
			a = h.get(ctr);
			for (int ctr2 = ctr+1; ctr2 < h.getSize()-1; ctr2++){
				b = h.get(ctr2);
				if (a.getFace() == b.getFace()){
					for (int ctr3 = ctr2+1; ctr3 < h.getSize(); ctr3++){
						c = h.get(ctr3);
						if (a.getFace() == c.getFace()){
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	public boolean hasStraight(Hand h){
		Card c;
		boolean[] cardValues = new boolean[CardDeck.faces.length+1];
		for (int ctr = 0; ctr < h.getSize(); ctr++){
			c = h.get(ctr);
			if (c.getFace().equals("A")){
				cardValues[0] = true;
				cardValues[CardDeck.faces.length] = true;
			} else {
				cardValues[c.getFaceValue()+1] = true;
			}
		}
		for (int ctr = 0; ctr < cardValues.length-5; ctr++){
			if (cardValues[ctr] && cardValues[ctr+1] && cardValues[ctr+2] && 
				cardValues[ctr+3] && cardValues[ctr+4]){
				return true;
			}
		}
		return false;
	}
	public boolean hasFlush(Hand h){
		int suitCount;
		Card c;
		for (int ctr = 0; ctr < 4; ctr++){
			suitCount = 0;
			for (int ctr2 = 0; ctr2 < h.getSize(); ctr2++){
				c = h.get(ctr2);
				if (c.getFace() == CardDeck.suits[ctr]){
					suitCount++;
					if (suitCount == 5){
						return true;
					}
				}
			}
		}
		return false;
	}
	public boolean hasFullHouse(Hand h){
		Card a,b,c;
		for (int ctr = 0; ctr < h.getSize()-2; ctr++){
			a = h.get(ctr);
			for (int ctr2 = ctr+1; ctr2 < h.getSize()-1; ctr2++){
				b = h.get(ctr2);
				if (a.getFace() == b.getFace()){
					for (int ctr3 = ctr2+1; ctr3 < h.getSize(); ctr3++){
						c = h.get(ctr3);
						if (a.getFace() == c.getFace()){
							h.remove(a);
							h.remove(b);
							h.remove(c);
							return hasPair(h);
						}
					}
				}
			}
		}
		return false;
	}
	public boolean hasFourOfAKind(Hand h){
		Card a,b,c,d;
		for (int ctr = 0; ctr < h.getSize()-3; ctr++){
			a = h.get(ctr);
			for (int ctr2 = ctr+1; ctr2 < h.getSize()-2; ctr2++){
				b = h.get(ctr2);
				if (a.getFace() == b.getFace()){
					for (int ctr3 = ctr2+1; ctr3 < h.getSize()-1; ctr3++){
						c = h.get(ctr3);
						if (a.getFace() == c.getFace()){
							for (int ctr4 = ctr3+1; ctr4 < h.getSize(); ctr4++){
								d = h.get(ctr4);
								if (a.getFace() == d.getFace()){
									return true;
								}
							}
							
						}
					}
				}
			}
		}
		return false;
	}
	// This currently does not check for A-5 straight flush
	public boolean hasStraightFlush(Hand h){
		Card a,b,c,d,e;
		for (int ctr = 0; ctr < h.getSize()-4; ctr++){
			a = h.get(ctr);
			for (int ctr2 = ctr+1; ctr2 < h.getSize()-3; ctr2++){
				b = h.get(ctr2);
				if (a.getFaceValue() == b.getFaceValue()+1 
						&& a.getSuit().equals(b.getSuit())){
					for (int ctr3 = ctr2+1; ctr3 < h.getSize()-2; ctr3++){
						c = h.get(ctr3);
						if (b.getFaceValue() == c.getFaceValue()+1 
								&& b.getSuit().equals(c.getSuit())){
							for (int ctr4 = ctr3+1; ctr4 < h.getSize()-1; ctr4++){
								d = h.get(ctr4);
								if (c.getFaceValue() == d.getFaceValue()+1 
										&& c.getSuit().equals(d.getSuit())){
									for (int ctr5 = ctr4+1; ctr5 < h.getSize(); ctr5++){
										e = h.get(ctr5);
										if (d.getFaceValue() == e.getFaceValue()+1 
												&& d.getSuit().equals(e.getSuit())){
											return true;
										}
									}
								}
							}
							
						}
					}
				}
			}
		}
		return false;
	}
	
	@Override
	public void showGameStats() {
		int totalPlayers;
		saveAllPlayers();
		totalPlayers = getTotalPlayers();
		bot.sendMessage(channel, formatNumber(totalPlayers)+" player(s) have played " +
					getGameNameStr()+".");
	}

	@Override
	public void showTopPlayers(String param, int n) {
		int highIndex;
		saveAllPlayers();
		try {
			ArrayList<String> nicks = new ArrayList<String>();
			ArrayList<Integer> stacks = new ArrayList<Integer>();
			ArrayList<Integer> bankrupts = new ArrayList<Integer>();
			ArrayList<Integer> debts = new ArrayList<Integer>();
			ArrayList<Integer> bjrounds = new ArrayList<Integer>();
			ArrayList<Integer> tprounds = new ArrayList<Integer>();
			ArrayList<Boolean> simples = new ArrayList<Boolean>();
			loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
			ArrayList<Integer> test = new ArrayList<Integer>();
			String title = Colors.BLACK + ",08Top " + n;
			String list;
			if (param.equals("cash")) {
				test = stacks;
				title += " Cash:";
			} else if (param.equals("debt")) {
				test = debts;
				title += " Debt:";
			} else if (param.equals("bankrupts")) {
				test = bankrupts;
				title += " Bankrupts:";
			} else if (param.equals("net") || param.equals("netcash")) {
				for (int ctr = 0; ctr < nicks.size(); ctr++) {
					test.add(stacks.get(ctr) - debts.get(ctr));
				}
				title += " Net Cash:";
			} else if (param.equals("rounds")) {
				test = tprounds;
				title += " Texas Hold'em Poker Rounds:";
			} else {
				throw new IllegalArgumentException();
			}
			list = title;
			for (int ctr = 1; ctr <= n; ctr++){
				highIndex = 0;
				for (int ctr2 = 0; ctr2 < nicks.size(); ctr2++) {
					if (test.get(ctr2) > test.get(highIndex)) {
						highIndex = ctr2;
					}
				}
				if (param.equals("rounds") || param.equals("bankrupts")) {
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
				if (nicks.isEmpty()) {
					break;
				}
			}
			bot.sendMessage(channel, list);
		} catch (IOException e) {
			System.out.println("Error reading players.txt!");
		}
	}

	@Override
	public void showPlayerRounds(String nick) {
		int rounds = getPlayerStat(nick, "tprounds");
		if (rounds != Integer.MIN_VALUE) {
			bot.sendMessage(channel, nick + " has played " + rounds
					+ " round(s) of " + getGameNameStr() + ".");
		} else {
			bot.sendMessage(channel, "No data found for " + nick + ".");
		}
	}
	
	@Override
	public void showReloadSettings() {
		bot.sendMessage(channel, "texaspoker.ini has been reloaded.");
	}

	public void showPlayerHand(TexasPokerPlayer p, Hand h) {
		bot.sendMessage(channel, p.getNickStr() + ": " + h);
	}
	public void showTablePlayers(){
		TexasPokerPlayer p;
		String outStr = getNumberJoined()+ " player(s): ";
        
		for (int ctr = 0; ctr < getNumberJoined(); ctr++){
			p = (TexasPokerPlayer) getJoined(ctr);
			outStr += p.getNick(); 
			if (p == dealer){
				outStr += "(D)";
			}
			if (p == smallBlind){
				outStr += "(SB)";
			}
			if (p == bigBlind){
				outStr += "(BB)";
			}
			outStr += " ";
		}
		bot.sendMessage(channel, outStr);
	}
	public void showCommunityCards(){
		bot.sendMessage(channel, "Community cards: " + community.toString());
	}
	public void showTurn(Player p) {
		TexasPokerPlayer TPp = (TexasPokerPlayer) p;
		bot.sendMessage(channel, p.getNickStr()+"'s turn. "+p.getNickStr()+" is in for $"+formatNumber(TPp.getBet())+". Stack: $" + formatNumber(p.getCash()-TPp.getBet())
							+ ". Current bet: $" + formatNumber(currentBet) + ".");
	}
	public void showBet(TexasPokerPlayer p){
		bot.sendMessage(channel, p.getNickStr()+" is in for $"+formatNumber(p.getBet())+ 
						". Stack: $"+formatNumber(p.getCash()-p.getBet()));
	}
	public void showFold(TexasPokerPlayer p){
		bot.sendMessage(channel, p.getNickStr()+" has folded. Stack: $"+formatNumber(p.getCash()-p.getBet()));
	}
	
	public void infoPlayerHand(TexasPokerPlayer p, Hand h) {
		if (p.isSimple()) {
			bot.sendNotice(p.getNick(), "Your hand is " + h + ".");
		} else {
			bot.sendMessage(p.getNick(), "Your hand is " + h + ".");
		}
	}
	public void infoBetTooLow(String nick, int min){
        bot.sendNotice(nick, "Minimum bet is $" + formatNumber(min) + ". Try again.");
    }
	public void infoMustAllIn(String nick){
        bot.sendNotice(nick, "You must go all in or fold. Try again.");
    }
	
	@Override
	public String getGameRulesStr() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getGameCommandStr() {
		return "start (go), join (j), leave (quit, l, q), bet (b), check/call (c), " +
				"table, turn, hand, cash, netcash (net), debt, bankrupts, rounds," +
				"simple, players, waitlist, blacklist, top5, gamehelp (ghelp), " +
				"gamerules (grules), gamecommands (gcommands)";
	}
}
