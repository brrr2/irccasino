package irccasino;

import java.util.*;

import org.pircbotx.*;
import org.pircbotx.hooks.events.*;

public class Blackjack extends CardGame {
    public Player dealer;
    public boolean insuranceBets;
    
    public Blackjack(PircBotX parent, Channel gameChannel){
        super(parent,gameChannel);
        gameName = "Blackjack";
        dealer = new Player(bot.getUserBot(),true);
        deck = new CardDeck(4);
        deck.shuffleCards();
        insuranceBets = false;
    }
    
    @Override
    public void onPart(PartEvent event){
        User user = event.getUser();
        leaveGame(user);
    }
    @Override
    public void onQuit(QuitEvent event){
        User user = event.getUser();
        leaveGame(user);
    }
    @Override
    public void onMessage(MessageEvent event){
        String msg = event.getMessage().toLowerCase();
        
        if (msg.charAt(0) == '.'){
            User user = event.getUser();

            if (msg.equals(".join") || msg.equals(".j")){
                if (playerJoined(user)){
                    bot.sendNotice(user,"You have already joined!");
                } else if (isInProgress()){
                    bot.sendNotice(user,"A round is already in progress. Please join the next round.");
                } else if (isBlacklisted(user)){
                    bot.sendNotice(user, "You have gone bankrupt. Please wait for a loan to join again.");
                } else {
                    addPlayer(user);
                }
            } else if (msg.equals(".leave") || msg.equals(".quit")){
                if (!playerJoined(user)){
                    bot.sendNotice(user,"You are not currently joined!");
                } else {
                    leaveGame(user);
                }
            } else if (msg.equals(".start") || msg.equals(".go")){
                if (isInProgress()){
                    event.respond("A round is already in progress!");
                } else if (!playerJoined(user)){
                    bot.sendNotice(user,"You are not currently joined!");
                } else if (getNumberPlayers()>0){
                	showStartRound();
                    showPlayers();
                    setInProgress(true);
                    setBetting(true);
                    Timer t = new Timer();
                    t.schedule(new StartRoundTask(this), 5000);
                } else {
                    showNoPlayers();
                }
            } else if (msg.startsWith(".bet ") || msg.startsWith(".b ")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
                	bot.sendNotice(user,"No round in progress!");
                } else if (!isBetting()){
                	bot.sendNotice(user,"No betting in progress!");
                } else if (!(currentPlayer == findPlayer(user))){
                	bot.sendNotice(user,"It's not your turn!");                    
                } else {
                	try{
                        int amount = parseNumberParam(msg);
                        bet(amount);
                    } catch (NumberFormatException e){
                        showImproperBet();
                    }
                }
            } else if (msg.equals(".hit")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
                	bot.sendNotice(user,"No round in progress!");
                } else if (isBetting()){
                	bot.sendNotice(user,"No cards have been dealt yet!");
                } else if (!(currentPlayer == findPlayer(user))){
                	bot.sendNotice(user,"It's not your turn!");
                } else {
                	hit();
                }
            } else if (msg.equals(".stay") || msg.equals(".stand")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
                	bot.sendNotice(user,"No round in progress!");
                } else if (isBetting()){
                	bot.sendNotice(user,"No cards have been dealt yet!");
                } else if (!(currentPlayer == findPlayer(user))){
                	bot.sendNotice(user,"It's not your turn!");
                } else {
                	stay();
                }
            } else if (msg.equals(".doubledown") || msg.equals(".dd")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
                	bot.sendNotice(user,"No round in progress!");
                } else if (isBetting()){
                	bot.sendNotice(user,"No cards have been dealt yet!");
                } else if (!(currentPlayer == findPlayer(user))){
                	bot.sendNotice(user,"It's not your turn!");
                } else {
                	doubleDown();
                } 
            } else if (msg.equals(".surrender") || msg.equals(".")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
                	bot.sendNotice(user,"No round in progress!");
                } else if (isBetting()){
                	bot.sendNotice(user,"No cards have been dealt yet!");
                } else if (!(currentPlayer == findPlayer(user))){
                	bot.sendNotice(user,"It's not your turn!");
                } else {
                	surrender();
                }
            } else if (msg.startsWith(".insure ")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
                	bot.sendNotice(user,"No round in progress!");
                } else if (isBetting()){
                	bot.sendNotice(user,"No cards have been dealt yet!");
                } else if (!(currentPlayer == findPlayer(user))){
                	bot.sendNotice(user,"It's not your turn!");
                } else {
                	try {
                        int amount = parseNumberParam(msg);
                        insure(amount);
                    } catch (NumberFormatException e){
                        showImproperBet();
                    }
                }
            } else if (msg.equals(".split")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
                	bot.sendNotice(user,"No round in progress!");
                } else if (isBetting()){
                	bot.sendNotice(user,"No cards have been dealt yet!");
                } else if (!(currentPlayer == findPlayer(user))){
                	bot.sendNotice(user,"It's not your turn!");
                } else {
                	bot.sendNotice(user, "Not yet implemented. Stay tuned!");
                }
            } else if (msg.equals(".table")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
            		bot.sendNotice(user,"No round in progress!");
            	} else if (isBetting()){
            		bot.sendNotice(user,"No cards have been dealt yet!");
            	} else {
                    showTableHands();
                }
	        } else if (msg.equals(".sum")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
            		bot.sendNotice(user,"No round in progress!");
            	} else if (isBetting()){
            		bot.sendNotice(user,"No cards have been dealt yet!");
            	} else {
            		infoPlayerSum(user);
            	}
            } else if (msg.equals(".hand")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
            		bot.sendNotice(user,"No round in progress!");
            	} else if (isBetting()){
            		bot.sendNotice(user,"No cards have been dealt yet!");
            	} else {
            		infoPlayerHand(user);
            	}
            } else if (msg.equals(".mybet")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
            		bot.sendNotice(user,"No round in progress!");
            	} else if (!findPlayer(user).hasBet()){
            		bot.sendNotice(user,"You have not made a bet yet!");
            	} else {
            		infoPlayerBet(user);
            	}
            } else if (msg.equals(".turn")){
	        	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
            		bot.sendNotice(user,"No round in progress!");
            	} else {
                    showPlayerTurn(currentPlayer);
                }
            } else if (msg.equals(".cash")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else {
            		infoPlayerCash(user);
            	}
            } else if (msg.equals(".simple")){
                if (!playerJoined(user)){
                	bot.sendNotice(user,"You are not currently joined!");
                } else {
                    togglePlayerSimple(user);
                }
            } else if (msg.equals(".players")){
                showPlayers();
            } else if (msg.equals(".gamerules") || msg.equals(".grules")){
                bot.sendNotice(user, "Not yet implemented. Good luck!");
            } else if (msg.equals(".gamehelp") || msg.equals(".ghelp")){
                bot.sendNotice(user, "Not yet implemented. Good luck!");
            } else if (msg.equals(".gamecommands") || msg.equals(".gcommands")){
                infoGameCommands(user);
            } else if (msg.equals(".currentgame") || msg.equals(".game")){
                showGameName();
            }  else if (msg.equals(".numdecks") || msg.equals(".ndecks")){
                infoNumDecks(user);
            } else if (msg.equals(".numcards") || msg.equals(".ncards")){
                if (channel.isOp(user)){
                    infoNumCards(user);
                } else {
                    bot.sendNotice(user,"Debugging commands may only be used by ops.");
                }
            } else if (msg.equals(".numdiscards") || msg.equals(".ndiscards")){
                if (channel.isOp(user)){
                    infoNumDiscards(user);
                } else {
                    bot.sendNotice(user,"Debugging commands may only be used by ops.");
                }
            } else if (msg.startsWith(".cards ") || msg.startsWith(".discards ")){
                if (channel.isOp(user)){
                	try{
                		int num = parseNumberParam(msg);
                		if (msg.startsWith(".cards ") && deck.getNumberCards() > 0){
                    		infoDeckCards(user,'c', num);
                    	} else if (msg.startsWith(".discards ") && deck.getNumberDiscards() > 0){
                    		infoDeckCards(user,'d', num);
                    	} else {
                    		bot.sendNotice(user,"Empty!");
                    	}
                    } catch (NumberFormatException e){
                    	bot.sendNotice(user,"Bad parameter!");
                    }
                } else {
                    bot.sendNotice(user,"Debugging commands may only be used by ops.");
                }
            }
        }
    }
    
    /* Game management methods */
    @Override
    public void leaveGame(User u){
        if (playerJoined(u)){
            Player p = findPlayer(u);
            if (isInProgress()){
                if (p == currentPlayer){
                    currentPlayer = getNextPlayer();
                    removePlayer(u);
                    if (currentPlayer == null){
                        if (isBetting()){
                            setBetting(false);
                            if (getNumberPlayers()==0){
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
    public void startRound(){
        currentPlayer = players.get(0);
        showPlayerTurn(currentPlayer);
        setIdleOutTimer();
    }
    @Override
    public void endRound(){
        Player p;
        setInProgress(false);
        if (getNumberPlayers()>0){
            showPlayerTurn(dealer);
            while(getCardSum(dealer)<17){
            	dealOne(dealer);
                showPlayerHand(dealer);
            }
            if (isHandBlackjack(dealer)){
            	showBlackjack(dealer);
            } else if (isHandBusted(dealer)){
            	showBusted(dealer);
            }
            showResults();
            if (hasInsuranceBets()){
            	showInsuranceResults();
            }
            for (int ctr=0; ctr<getNumberPlayers(); ctr++){
                p = getPlayer(ctr);
                if(isPlayerBankrupt(p)){
                    blacklist.add(p);
                    infoPlayerBankrupt(p.getUser());
                    bot.sendMessage(channel, p.getNickStr()+" has gone bankrupt. S/He has been kicked to the curb.");
                    removePlayer(p.getUser());
                    Timer t = new Timer();
                    t.schedule(new RespawnTask(p,this), 180000);
                }
            }
        } else {
            showNoPlayers();
        }
        resetGame();
        showEndRound();
    }
    @Override
    public void endGame(){
        Player p;
        for (int ctr=0; ctr<getNumberPlayers(); ctr++){
            p = getPlayer(ctr);
            savePlayerData(p);
        }
        players.clear();
        deck = null;
        dealer = null;
        currentPlayer = null;
    }
    @Override
    public void resetGame(){
        discardPlayerHand(dealer);
        setInsuranceBets(false);
        resetPlayers();
    }
    @Override
    public void resetPlayers(){
        BlackjackPlayer p;
        for (int ctr = 0; ctr < getNumberPlayers(); ctr++){
            p = (BlackjackPlayer) players.get(ctr);
            discardPlayerHand(p);
            p.clearBet();
            p.clearInsureBet();
            p.setSurrender(false);
            p.setHit(false);
        }
    } 
    
    /* Player management methods */
    @Override
    public void addPlayer(User u){
        Player p = new BlackjackPlayer(u,false);
        players.add(0,p);
        loadPlayerData(p);
        showPlayerJoin(p);
    }
    
    /* Calculations for blackjack */
    public int getCardValue(Card c){
        int num;
        try { 
            num = Integer.parseInt(c.getFace()); 
        } catch(NumberFormatException e) { 
            return 10;
        }
        return num;
    }
    public int getCardSum(Player p){
        int sum = 0, numAces=0;
        int numCards = p.getHandSize();
        ArrayList<Card> pCards = p.getHand();
        Card card;
        //sum the non-aces first and store number of aces in the hand
        for (int ctr = 0; ctr < numCards; ctr++){
        	card = pCards.get(ctr);
            if (card.getFace().equals("A")){
                numAces++;
            } else {
                sum += getCardValue(card);
            }
        }
        //find biggest non-busting sum with aces
        if (numAces > 0){
            if ((numAces-1)+11+sum>21){
                sum += numAces;
            } else {
                sum += (numAces-1)+11;
            }
        }
        return sum;
    }
    public int calcHalf(int amount){
        return (int)(Math.ceil((double)(amount)/2.));
    }
    
    /* Card dealing for Blackjack */
    public void dealOne(Player p){
    	p.addCard(deck.takeCard());
    	if (deck.getNumberCards() == 0){
    		showDeckEmpty();
    		deck.refillDeck();
    	}
    }
    public void dealHand(Player p){
    	for (int ctr2=0; ctr2<2; ctr2++){
        	p.addCard(deck.takeCard());
        	if (deck.getNumberCards() == 0){
        		showDeckEmpty();
        		deck.refillDeck();
        	}
        }
    }
    public void dealTable(){
        Player p;
        for (int ctr=0; ctr<getNumberPlayers(); ctr++){
            p = players.get(ctr);
            dealHand(p);
            infoPlayerHand(p.getUser());
        }
        dealHand(dealer);
        showTableHands();
    }
    
    /* Direct Blackjack command methods */
    public void stay(){
        cancelIdleOutTimer();
        currentPlayer = getNextPlayer();
        if (currentPlayer == null){
            endRound();
        } else {
            quickEval();
        }
    }
    public void hit(){
        cancelIdleOutTimer();
        BlackjackPlayer BJp = (BlackjackPlayer)currentPlayer;
        BJp.setHit(true);
        dealOne(currentPlayer);
        showPlayerHand(currentPlayer);
        if (isHandBusted(currentPlayer)){
        	showBusted(currentPlayer);
            currentPlayer = getNextPlayer();
            if (currentPlayer == null){
                endRound();
            } else {
                quickEval();
            }
        } else {
            setIdleOutTimer();
        }
    }
    public void doubleDown(){
        cancelIdleOutTimer();
        BlackjackPlayer BJp = (BlackjackPlayer)currentPlayer;
        if (BJp.hasHit()){
        	showNotDoubleDown();
        	setIdleOutTimer();
        } else if (currentPlayer.getBet() > currentPlayer.getCash()){
            showInsufficientFunds();
            setIdleOutTimer();
        } else {
            currentPlayer.addBet(currentPlayer.getBet());
            dealOne(currentPlayer);
            showDoubleDown(currentPlayer);
            showPlayerHand(currentPlayer);
            if (isHandBusted(currentPlayer)){
            	showBusted(currentPlayer);
            }
            currentPlayer = getNextPlayer();
            if (currentPlayer == null){
                endRound();
            } else {
                quickEval();
            }
        }
    }
    public void surrender(){
        cancelIdleOutTimer();
        BlackjackPlayer BJp = (BlackjackPlayer)currentPlayer;
        if (BJp.hasHit()){
            showNotSurrender();
            setIdleOutTimer();
        } else {
            currentPlayer.addBet(-1*calcHalf(currentPlayer.getBet()));
            BJp.setSurrender(true);
            showSurrender(currentPlayer);
            currentPlayer = getNextPlayer();
            if (currentPlayer == null){
                endRound();
            } else {
                quickEval();
            }
        }
    }
    public void insure(int amount){
    	cancelIdleOutTimer();
    	BlackjackPlayer BJp = (BlackjackPlayer)currentPlayer;
    	if (BJp.hasInsured()){
			showAlreadyInsured();
		} else if (!dealerUpcardAce()){
    		showNotInsure();
    	} else {
    		if (amount > calcHalf(currentPlayer.getBet())){
    			showInsureBetTooHigh(currentPlayer);
    		} else if (amount <= 0){
    			showBetTooLow();
    		} else {
    			setInsuranceBets(true);
    			BJp.setInsureBet(amount);
    			showInsure(BJp);
    		}
    	}
    	setIdleOutTimer();
    }
    public void bet(int amount){
        cancelIdleOutTimer();
        if (amount > calcHalf(currentPlayer.getCash())){
            showBetTooHigh(currentPlayer);
            setIdleOutTimer();
        } else if (amount <= 0){
            showBetTooLow();
            setIdleOutTimer();
        } else {
            currentPlayer.addBet(amount);
            showProperBet(currentPlayer);
            currentPlayer = getNextPlayer();
            if (currentPlayer == null){
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
    
    /* Blackjack behind-the-scenes methods */
    public void quickEval(){
    	BlackjackPlayer BJp = (BlackjackPlayer) currentPlayer;
        showPlayerTurn(currentPlayer);
        
        if (isHandBusted(currentPlayer)){
        	showBusted(currentPlayer);
            currentPlayer = getNextPlayer();
            if (currentPlayer == null){
                endRound();
                return;
            }
            showPlayerTurn(currentPlayer);
        } else if (!BJp.hasHit() && isHandBlackjack(currentPlayer)){
        	showBlackjack(currentPlayer);
        }
        setIdleOutTimer();
    }
    public boolean isHandBlackjack(Player p){
    	int sum = getCardSum(p);
    	if (sum == 21 && p.getHandSize() == 2){
    		return true;
    	}
    	return false;
    }
    public boolean isHandBusted(Player p){
        int sum = getCardSum(p);
        if (sum > 21){
            return true;
        }
        return false;
    }
    public int evaluateHand(Player p){
        int sum = getCardSum(p), dsum = getCardSum(dealer);
        boolean pBlackjack = isHandBlackjack(p);
        boolean dBlackjack = isHandBlackjack(dealer);
        if (sum > 21){
            return -1;
        } else if (sum == 21){
        	/* Different cases at 21 */
        	if (pBlackjack && !dBlackjack){
             	p.addCash(2*p.getBet()+calcHalf(p.getBet()));
                 return 2;
            } else if (pBlackjack && dBlackjack){
                p.addCash(p.getBet());
                return 0;
            } else if (!pBlackjack && dBlackjack){
            	return -1;
            } else {
            	if (dsum == 21){
	                p.addCash(p.getBet());
	                return 0;
            	} else {
            		p.addCash(2*p.getBet());
	                return 1;
            	}
            }
        } else {
        	/* Any case other than 21 */
            if (dsum > 21 || dsum < sum){
                p.addCash(2*p.getBet());
                return 1;
            } else if (dsum == sum){
            	p.addCash(p.getBet());
                return 0;
            } else {
                return -1;
            }       
        }
    }
    public int evaluateInsurance(BlackjackPlayer p){
    	if (isHandBlackjack(dealer)){
    		p.addCash(3*p.getInsureBet());
    		return 1;
    	} else {
    		return -1;
    	}
    }
    public boolean dealerUpcardAce(){
    	if (dealer.hasHand()){
    		ArrayList<Card> cards = dealer.getHand();
    		if (cards.get(1).getFace().equals("A")){
    			return true;
    		} else {
    			return false;
    		}
    	}
    	return false;
    }
    public boolean hasInsuranceBets(){
    	return insuranceBets;
    }
    public void setInsuranceBets(boolean b){
    	insuranceBets = b;
    }
    
    /* Channel output methods for Blackjack */
    @Override
    public void showPlayerTurn(Player p){
        if (isBetting()){
            bot.sendMessage(channel, p.getNickStr()+"'s turn. Stack: $"+p.getCash()+". Enter a bet up to $"+calcHalf(p.getCash())+".");
        } else {
            bot.sendMessage(channel,"It's now "+p.getNickStr()+"'s turn.");
        }
    }
    @Override
    public void showPlayerHand(Player p){
        bot.sendMessage(channel, p.getNickStr()+": "+p.getCardStr(1));
    }
    public void showDeckEmpty(){
    	bot.sendMessage(channel,"The deck is now empty. Refilling the deck.");
    }
    public void showProperBet(Player p){
        bot.sendMessage(channel,p.getNickStr()+" bets $"+p.getBet()+". Stack: $"+p.getCash());
    }
    public void showImproperBet(){
        bot.sendMessage(channel,"Improper bet. Try again.");
    }
    public void showBetTooLow(){
        bot.sendMessage(channel, "Minimum bet is $1. Try again.");
    }
    public void showBetTooHigh(Player p){
        bot.sendMessage(channel, "Maximum bet is $"+calcHalf(p.getCash())+". Try again.");
    }
    public void showInsureBetTooHigh(Player p){
    	bot.sendMessage(channel, "Maximum insurance bet is $"+calcHalf(p.getBet())+". Try again.");
    }
    public void showInsufficientFunds(){
    	bot.sendMessage(channel, "Insufficient funds. Try again.");
    } 
    public void showNotDoubleDown(){
        bot.sendMessage(channel, "You can only double down before hitting!");
    }
    public void showDoubleDown(Player p){
        bot.sendMessage(channel, p.getNickStr()+" has doubled down! Total bet now $"+p.getBet()+". Stack: $"+p.getCash());
    }
    public void showNotSurrender(){
        bot.sendMessage(channel, "You can only surrender before hitting!");
    }
    public void showSurrender(Player p){
        bot.sendMessage(channel, p.getNickStr()+" has surrendered! Half the bet is returned and the rest forfeited. Stack: $"+p.getCash());
    }
    public void showNotInsure(){
    	bot.sendMessage(channel, "The dealer's upcard is not an A. You cannot make an insurance bet.");
    }
    public void showAlreadyInsured(){
    	bot.sendMessage(channel, "You have already made an insurance bet.");
    }
    public void showInsure(BlackjackPlayer p){
    	bot.sendMessage(channel, p.getNickStr()+" has made an insurance bet of $"+p.getInsureBet()+". Stack: $"+p.getCash());
    }
    public void showDealingTable(){
    	bot.sendMessage(channel, Colors.BOLD+Colors.DARK_GREEN+"Dealing..."+Colors.NORMAL);
    }
    public void showBusted(Player p){
        bot.sendMessage(channel, p.getNickStr()+" has busted!");
    }
    public void showBlackjack(Player p){
        bot.sendMessage(channel, p.getNickStr()+" has blackjack!");
    }
    public void showTableHands(){
        Player p;
        bot.sendMessage(channel,Colors.DARK_GREEN+Colors.BOLD+"Table:"+Colors.NORMAL);
        for (int ctr=0; ctr<getNumberPlayers(); ctr++){
            p = players.get(ctr);
            showPlayerHand(p);
        }
        showPlayerHand(dealer);
    }
    public void showInsuranceResults(){
    	BlackjackPlayer p;
    	bot.sendMessage(channel, Colors.BOLD+Colors.DARK_GREEN+"Insurance Results:"+Colors.NORMAL);
    	if (getCardSum(dealer) == 21){
    		bot.sendMessage(channel,dealer.getNickStr()+" had blackjack.");
    	} else {
    		bot.sendMessage(channel,dealer.getNickStr()+" did not have blackjack.");
    	}
    	for (int ctr=0; ctr<getNumberPlayers(); ctr++){
    		p = (BlackjackPlayer) getPlayer(ctr);
    		if (p.hasInsured()){
    			showPlayerInsuranceResult(p);
    		}
    	}
    }
    public void showResults(){
        Player p;
        bot.sendMessage(channel, Colors.BOLD+Colors.DARK_GREEN+"Results:"+Colors.NORMAL);
        showDealerResult();
        for (int ctr=0; ctr<getNumberPlayers(); ctr++){
            p = getPlayer(ctr);
            showPlayerResult(p);
        }
    }
    public void showDealerResult(){
        int sum = getCardSum(dealer);
        String outStr = dealer.getNickStr();
        if (sum > 21){
        	outStr += " has busted! (";
        } else if (isHandBlackjack(dealer)){
        	outStr += " has blackjack! (";
        } else {
        	outStr += " has "+sum+". (";
        }
        outStr += dealer.getCardStr(0)+")";
        bot.sendMessage(channel, outStr);
    }
    public void showPlayerResult(Player p){
        String outStr = p.getNickStr();
        BlackjackPlayer BJp = (BlackjackPlayer) p;
        int result = evaluateHand(p);
        if (BJp.hasSurrendered()){
            outStr += " surrendered. (";
        } else if (result == 2){
        	outStr += " wins $"+(2*p.getBet()+calcHalf(p.getBet()))+". (";
        } else if (result == 1){
            outStr += " wins $"+(2*p.getBet())+". (";
        } else if (result == 0){
            outStr += " pushes and his/her $"+p.getBet()+" bet is returned. (";
        } else {
            outStr += " loses. (";    
        }
        outStr += p.getCardStr(0)+") Stack: $"+p.getCash();
        bot.sendMessage(channel, outStr);
    }
    public void showPlayerInsuranceResult(BlackjackPlayer p){
    	String outStr;
        int result = evaluateInsurance(p);
        if (result == 1){
            outStr = p.getNickStr()+" wins $"+3*p.getInsureBet()+".";
        } else {
            outStr = p.getNickStr()+" loses.";    
        }
        outStr += " Stack: $"+p.getCash();
        bot.sendMessage(channel, outStr);
    }
    
    /* Private messages */
    public void infoPlayerSum(User user){
        Player p = findPlayer(user);
        if (p.isSimple()){
            bot.sendNotice(user,"You have: "+getCardSum(p)+".");
        } else {
            bot.sendMessage(user,"You have: "+getCardSum(p)+".");
        }
    }
    @Override
    public void infoPlayerBet(User user){
        BlackjackPlayer p = (BlackjackPlayer) findPlayer(user);
        String outStr = "You have bet $"+p.getBet();
        if (p.hasInsured()){
        	outStr += " with an insurance bet of $"+p.getInsureBet();
        }
        outStr += ".";
        if (p.isSimple()){
            bot.sendNotice(user, outStr);
        } else {
            bot.sendMessage(user, outStr);
        }
    }
    
    /* Formatted strings */
    @Override
    public String getGameNameStr(){
    	return Colors.BOLD+gameName+Colors.NORMAL;
    }
    @Override
    public String getGameCommandStr(){
    	return "start (go), join (j), leave (quit), bet (b), hit, stay (stand), doubledown (dd), surrender, " +
    			"insure, table, turn, sum, cash, hand, mybet, simple, players, gamehelp (ghelp), " +
    			"gamerules (grules), gamecommands (gcommands)";
    }
}