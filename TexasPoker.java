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
			}
		}
	}
	
	public TexasPoker(PircBotX parent, Channel gameChannel, char c){
		super(parent, gameChannel, c);
		gameName = "Texas Hold'em Poker";
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
				} else if (getNumberJoined() > 0) {
					showStartRound();
					showPlayers();
					setInProgress(true);
					setBetting(true);
					setStartRoundTimer();
				} else {
					showNoPlayers();
				}
			} else if (msg.startsWith("bet ") || msg.startsWith("b ")
					|| msg.equals("bet") || msg.equals("b")) {
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

	@Override
	public void startRound() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void continueRound() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void endRound() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void endGame() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void resetGame() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void leave(String nick) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setIdleOutTimer() {
		idleOutTimer = new Timer();
		idleOutTimer.schedule(new IdleOutTask((TexasPokerPlayer) currentPlayer,
				this), idleOutTime*1000);
	}

	@Override
	public void cancelIdleOutTimer() {
		if (idleOutTimer != null) {
			idleOutTimer.cancel();
			idleOutTimer = null;
		}
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
				}
			}
			f.close();
		} catch (IOException e) {
			/* load defaults if texaspoker.ini is not found */
			System.out.println("texaspoker.ini not found! Creating new texaspoker.ini...");
			newcash = 1000;
			idleOutTime = 60;
			respawnTime = 600;
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

	public void bet(int amount) {
		cancelIdleOutTimer();
		TexasPokerPlayer p = (TexasPokerPlayer) currentPlayer;
		Hand h = p.getHand();
		if (amount > p.getCash()) {
			infoBetTooHigh(p.getNick(), p.getCash());
			setIdleOutTimer();
		} else if (amount <= 0) {
			infoBetTooLow(p.getNick());
			setIdleOutTimer();
		} else {
			h.addBet(amount);
			p.addCash(-1 * amount);
			showProperBet(p,h);
			currentPlayer = getNextPlayer();
			if (currentPlayer == null) {
			} else {
				showTurn(currentPlayer);
				setIdleOutTimer();
			}
		}
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

	public void showProperBet(TexasPokerPlayer p, Hand h) {
		bot.sendMessage(channel, p.getNickStr() + " bets $"
						+ formatNumber(h.getBet())
						+ ". Stack: $" + formatNumber(p.getCash()));
	}
	
	public void infoPlayerHand(TexasPokerPlayer p, Hand h) {
		if (p.isSimple()) {
			bot.sendNotice(p.getNick(), "Your hand is " + h + ".");
		} else {
			bot.sendMessage(p.getNick(), "Your hand is " + h + ".");
		}
	}
	
	@Override
	public String getGameRulesStr() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getGameCommandStr() {
		return "start (go), join (j), leave (quit, l, q), bet (b), table, " +
				"turn, hand, cash, netcash (net), debt, bankrupts, rounds," +
				"simple, players, waitlist, blacklist, top5, gamehelp (ghelp), " +
				"gamerules (grules), gamecommands (gcommands)";
	}
}
