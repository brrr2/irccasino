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

public class TexasPoker extends CardGame{
    
    private ArrayList<PokerPot> pots;
    private PokerPot currentPot;
    private PokerPlayer dealer, smallBlind, bigBlind, topBettor;
    private Hand community;
    private HouseStat house;
    
    /**
     * A pot class to handle bets and payouts in Texas Hold'em Poker.
     */
    private class PokerPot {
        private ArrayList<PokerPlayer> players;
        private ArrayList<PokerPlayer> donors;
        private int total;

        public PokerPot(){
            total = 0;
            players = new ArrayList<PokerPlayer>();
            donors = new ArrayList<PokerPlayer>();
        }

        protected int getTotal(){
            return total;
        }
        protected void add(int amount){
            total += amount;
        }
        protected void addPlayer(PokerPlayer p){
            players.add(p);
        }
        protected void removePlayer(PokerPlayer p){
            players.remove(p);
        }
        protected void addDonor(PokerPlayer p) {
            donors.add(p);
        }
        protected void removeDonor(PokerPlayer p) {
            donors.remove(p);
        }
        protected PokerPlayer getPlayer(int c){
            return players.get(c);
        }
        protected ArrayList<PokerPlayer> getPlayers(){
            return players;
        }
        protected PokerPlayer getDonor(int c) {
            return donors.get(c);
        }
        protected ArrayList<PokerPlayer> getDonors() {
            return donors;
        }
        protected boolean hasPlayer(PokerPlayer p){
            return players.contains(p);
        }
        protected boolean hasDonor(PokerPlayer p) {
            return donors.contains(p);
        }
        protected int getNumPlayers(){
            return players.size();
        }
        protected int getNumDonors() {
            return donors.size();
        }
    }
    
    /**
     * Stores game statistics for TexasPoker. 
     */
    private class HouseStat extends Stats {
        private ArrayList<PokerPlayer> donors;
        private ArrayList<PokerPlayer> winners;
        
        public HouseStat() {
            this(0);
        }
        
        public HouseStat(int pot) {
            set("biggestpot", pot);
            donors = new ArrayList<PokerPlayer>();
            winners = new ArrayList<PokerPlayer>();
        }
        
        protected int getNumDonors() {
            return donors.size();
        }
        protected void addDonor(PokerPlayer p){
            donors.add(p);
        }
        protected void clearDonors(){
            donors.clear();
        }
        protected int getNumWinners() {
            return winners.size();
        }
        protected void addWinner(PokerPlayer p){
            winners.add(p);
        }
        protected void clearWinners(){
            winners.clear();
        }
        
        protected String getDonorsString(){
            String outStr = "";
            for (int ctr = 0; ctr < donors.size(); ctr++){
                outStr += donors.get(ctr).getNick() + " ";
            }
            return outStr.substring(0, outStr.length() - 1);
        }
        
        protected String getWinnersString(){
            String outStr = "";
            for (int ctr = 0; ctr < winners.size(); ctr++){
                outStr += winners.get(ctr).getNick() + " ";
            }
            return outStr.substring(0, outStr.length() - 1);
        }
        
        protected String getToStringList(){
            String outStr;
            int size = donors.size();
            if (size == 0){
                outStr = formatBold("0") + " players";
            } else if (size == 1){
                outStr = formatBold("1") + " player: " + donors.get(0).getNickStr();
            } else {
                outStr = formatBold(size) + " players: ";
                for (int ctr = 0; ctr < size; ctr++){
                    if (ctr == size-1){
                        if (winners.contains(donors.get(ctr))) {
                            outStr += donors.get(ctr).getNickStr();
                        } else {
                            outStr += donors.get(ctr).getNick();
                        }
                    } else {
                        if (winners.contains(donors.get(ctr))) {
                            outStr += donors.get(ctr).getNickStr() + ", ";
                        } else {
                            outStr += donors.get(ctr).getNick() + ", ";
                        }
                    }
                }   
            }
            return outStr;
        }
        
        @Override
        public String toString() {
            return "Biggest pot: $" + formatNumber(get("biggestpot")) + " (" + getToStringList() + ").";
        }
    }
    
    /**
     * Extends Hand with extra methods for Poker hand comparisons.
     * @author Yizhe Shen
     */
    private class PokerHand extends Hand implements Comparable<PokerHand>{
        /** Stores the calculated value of the PokerHand. */
        private int value;

        /** Names of Poker hands indexed according to value. */
        private final String[] handNames = {"High Card","Pair","Two Pairs",
                                        "Three of a Kind","Straight",
                                        "Flush","Full House","Four of a Kind",
                                        "Straight Flush","Royal Flush"};

        /**
         * Creates a new PokerHand.
         */
        public PokerHand(){
            super();
            value = -1;
        }

        /**
         * Compares this PokerHand to another based on hand-type and cards.
         * Comparisons require that both hands be sorted in descending order and
         * that the cards representing the hand value be at the beginning of the
         * hand. This means that getValue() must be called before making
         * any comparisons. If the two PokerHands have the same value, individual
         * cards must then me examined.
         * 
         * @param h the PokerHand to compare
         * @return -1 if this hand's value is less, zero for a tie, or 1 
         * if this hand's value is greater
         * @throws NullPointerException if the specified PokerHand is null
         */
        @Override
        public int compareTo(PokerHand h){
            if (h == null) {
                throw new NullPointerException();
            }
            int thisValue = this.getValue();
            int otherValue = h.getValue();

            // Check if hands have same value
            if (thisValue == otherValue){
                // Calculate comparisons for each card
                int[] comps = new int[5];
                for (int ctr = 0; ctr < 5; ctr++){
                    comps[ctr] = this.get(ctr).getFaceValue() - h.get(ctr).getFaceValue();
                }

                switch (value) {
                    // Straight Flush and Straight: check top card of straight
                    case 9: case 8: case 4:
                        return comps[0];
                    // 4 of a Kind: compare 4 of a kind, then kicker
                    case 7: 
                        if (comps[0] == 0){
                            return comps[4];
                        }
                        return comps[0];
                    // Full House: check triplet, then pair
                    case 6:	
                        if (comps[0] == 0){
                                return comps[3];
                        }
                        return comps[0];
                    // Flush: check highest non-common card
                    case 5: 
                        for (int ctr = 0; ctr < 5; ctr++){
                            if (comps[ctr] != 0){
                                return comps[ctr];
                            }
                        }
                        return 0;
                    // Three of a Kind: check triplet, then check kickers
                    case 3:
                        if (comps[0] == 0){
                            for (int ctr = 3; ctr < 5; ctr++){
                                if (comps[ctr] != 0){
                                    return comps[ctr];
                                }
                            }
                            return 0;
                        }
                        return comps[0];
                    // Two pair: check each pair, then check kicker
                    case 2: 
                        if (comps[0] != 0){
                            return comps[0];
                        } else if (comps[2] != 0){
                            return comps[2];
                        }
                        return comps[4];
                    // Pair: check pair, then check highest non-common card
                    case 1:
                        if (comps[0] == 0){
                            for (int ctr = 2; ctr < 5; ctr++){
                                if (comps[ctr] != 0){
                                    return comps[ctr];
                                }
                            }
                            return 0;
                        }
                        return comps[0];
                    // High Card: check highest non-common card
                    default:
                        for (int ctr = 0; ctr < 5; ctr++){
                            if (comps[ctr] != 0){
                                return comps[ctr];
                            }
                        }
                        return 0;
                }
            }
            return thisValue - otherValue;
        }

        /**
         * Returns the value of this PokerHand for determining hand-type.
         * Calls calcValue() if it hasn't been called yet.
         * @return the value
         */
        protected int getValue(){
            if (value == -1){
                value = calcValue();
            }
            return value;
        }

        /**
         * Resets the value to the default.
         */
        protected void resetValue(){
            value = -1;
        }

        /**
         * Name of the hand.
         * @return the name of the hand based on value.
         */
        protected String getName(){
            return handNames[getValue()];
        }

        /**
         * Returns the String representation of a PokerHand.
         * This can be used for display purposes.
         * 
         * @return the top 5 cards forming the hand followed by the remaining cards. 
         */
        @Override
        public String toString(){
            String out;
            switch (this.getValue()) {
                // Royal flush, straight flush, full house, flush, straight, high card
                case 9: case 8: case 6: case 5: case 4: case 0: 
                    out = toString(0,5);
                    break;
                case 7: // 4 of a kind
                    out = toString(0,4)+"/"+toString(4,5);
                    break;
                case 3: // 3 of a kind
                    out = toString(0,3)+"/"+toString(3,5);
                    break;
                case 2: // 2 pairs
                    out = toString(0,4)+"/"+toString(4,5);
                    break;
                case 1: // 1 pair
                    out = toString(0,2)+"/"+toString(2,5);
                    break;
                default:
                    out = "";
            }
            if (this.getSize() > 5){
                out += "||"+toString(5, this.getSize());
            }
            return out;
        }

        /*
         * A collection of static methods to check for various poker card combinations.
         * These methods require that hands be sorted in descending order.
         */

        /**
         * Calculates the value of a PokerHand.
         * Poker hand-types are always searched in order of descending value. Once
         * the PokerHand is found to have a certain hand-type, the method stops
         * searching for hand-types of lower value.
         * 
         * @param h the PokerHand to be calculated
         * @return value corresponding to hand-type.
         */
        protected int calcValue(){
            // Always check the hands in order of descending value
            if (hasStraightFlush()){	
                if (get(0).getFace().equals("A")){
                    return 9; // Royal flush = 9
                }
                return 8;   // Straight flush = 8
            } else if (hasFourOfAKind()){	// Four of a kind = 7
                return 7;
            } else if (hasFullHouse()){	// Full house = 6
                return 6;
            } else if (hasFlush()){	// Flush = 5
                return 5;
            } else if (hasStraight()){	// Straight = 4
                return 4;
            } else if (hasThreeOfAKind()){	// Three of a kind = 3
                return 3;
            } else if (hasTwoPair()){	// Two-pair = 2
                return 2;
            } else if (hasPair()){	// Pair = 1
                return 1;
            } else {	// High card = 0
                return 0;
            }
        }

        /**
         * Determines if a PokerHand has a pair.
         * @param h the PokerHand to search
         * @return true if it contains a pair
         */
        protected boolean hasPair(){
            Card a,b;
            for (int ctr = 0; ctr < getSize()-1; ctr++){
                a = get(ctr);
                b = get(ctr+1);
                if (a.getFace().equals(b.getFace())){
                    remove(a);
                    remove(b);
                    add(a, 0);
                    add(b, 1);
                    return true;
                }
            }
            return false;
        }

        /**
         * Determines if a PokerHand has two pairs.
         * @param h the PokerHand to search
         * @return true if it contains two pairs
         */
        protected boolean hasTwoPair(){
            Card a,b;
            for (int ctr = 0; ctr < getSize()-3; ctr++){
                a = get(ctr);
                b = get(ctr+1);
                if (a.getFace().equals(b.getFace())){
                    remove(a);
                    remove(b);
                    if (hasPair()){
                        add(a, 0);
                        add(b, 1);
                        return true;
                    } else {
                        add(a, ctr);
                        add(b, ctr+1);
                        return false;
                    }
                }
            }
            return false;
        }

        /**
         * Determines if a PokerHand has three of a kind.
         * @param h the PokerHand to search
         * @return true if it contains three of a kind
         */
        protected boolean hasThreeOfAKind(){
            Card a,b,c;
            for (int ctr = 0; ctr < getSize()-2; ctr++){
                a = get(ctr);
                b = get(ctr+1);
                c = get(ctr+2);
                if (a.getFace().equals(b.getFace()) && b.getFace().equals(c.getFace())){
                    remove(a);
                    remove(b);
                    remove(c);
                    add(a, 0);
                    add(b, 1);
                    add(c, 2);
                    return true;
                }
            }
            return false;
        }

        /**
         * Determines if a PokerHand has a straight.
         * @param h the PokerHand to search
         * @return true if it contains a straight
         */
        protected boolean hasStraight(){
            /* Create a boolean array to determine which face cards exist in the hand.
             * An extra index is added at the beginning for the value duality of aces.
             */
            boolean[] cardValues = new boolean[CardDeck.faces.length+1];
            Card c;
            for (int ctr = 0; ctr < getSize(); ctr++){
                c = get(ctr);
                if (c.getFace().equals("A")){
                    cardValues[0] = true;
                }
                cardValues[c.getFaceValue()+1] = true;
            }
            // Determine if any sequence of 5 consecutive cards exist
            for (int ctr = cardValues.length-1; ctr >= 4; ctr--){
                if (cardValues[ctr] && cardValues[ctr-1] && cardValues[ctr-2] && 
                    cardValues[ctr-3] && cardValues[ctr-4]){
                    // Move the sequence in descending order to the beginning of the hand
                    for (int ctr2 = 0; ctr2 < 5; ctr2++){
                        for (int ctr3=0; ctr3 < getSize(); ctr3++){
                            c = get(ctr3);
                            if ((ctr-ctr2 == 0 && c.getFace().equals("A")) || c.getFaceValue()+1 == ctr-ctr2){
                                remove(c);
                                add(c,ctr2);
                                break;
                            }
                        }
                    }
                    return true;
                }
            }
            return false;
        }

        /**
         * Determines if a PokerHand has a flush.
         * @param h the PokerHand to search
         * @return true if it contains a flush
         */
        protected boolean hasFlush(){
            int[] suitCount = new int[CardDeck.suits.length];
            Card c;
            for (int ctr = 0; ctr < getSize(); ctr++){
                c = get(ctr);
                suitCount[c.getSuitValue()]++;
            }
            for (int ctr = 0; ctr < suitCount.length; ctr++){
                if (suitCount[ctr] >= 5){
                    int count = 0;
                    for (int ctr3=0; ctr3 < getSize(); ctr3++){
                        c = get(ctr3);
                        if (c.getSuitValue() == ctr){
                            remove(c);
                            add(c,count++);
                        }
                    }
                    return true;
                }
            }
            return false;
        }

        /**
         * Determines if a PokerHand has a full house.
         * @param h the PokerHand to search
         * @return true if it contains a full house
         */
        protected boolean hasFullHouse(){
            Card a,b,c;
            for (int ctr = 0; ctr < getSize()-2; ctr++){
                a = get(ctr);
                b = get(ctr+1);
                c = get(ctr+2);
                if (a.getFace().equals(b.getFace()) && a.getFace().equals(c.getFace())){
                    remove(a);
                    remove(b);
                    remove(c);
                    boolean hp = hasPair();
                    if (hp){
                        add(a,0);
                        add(b,1);
                        add(c,2);
                        return true;
                    } else {
                        add(a,ctr);
                        add(b,ctr+1);
                        add(c,ctr+2);
                        return false;
                    }
                }
            }
            return false;
        }

        /**
         * Determines if a PokerHand has four of a kind.
         * @param h the PokerHand to search
         * @return true if it contains four of a kind
         */
        protected boolean hasFourOfAKind(){
            Card a,b,c,d;
            for (int ctr = 0; ctr < getSize()-3; ctr++){
                a = get(ctr);
                b = get(ctr+1);
                c = get(ctr+2);
                d = get(ctr+3);
                if (a.getFace().equals(b.getFace()) && b.getFace().equals(c.getFace()) 
                        && c.getFace().equals(d.getFace())){
                    remove(a);
                    remove(b);
                    remove(c);
                    remove(d);
                    add(a,0);
                    add(b,1);
                    add(c,2);
                    add(d,3);
                    return true;
                }
            }
            return false;
        }

        /**
         * Determines if a PokerHand has a straight flush.
         * This method will terminate as soon a straight flush is found. The search
         * is done in the order the suits are indexed in CardDeck.suits. This means
         * that this method will not always reveal the highest straight flush of a 
         * hand. However, in most Poker variants, this will suffice.
         * 
         * @param h the PokerHand to search
         * @return true if it contains a straight flush
         */
        protected boolean hasStraightFlush(){
            int[] suitCount = new int[CardDeck.suits.length];
            Hand nonFlushCards = new Hand();
            Card c;
            for (int ctr = 0; ctr < getSize(); ctr++){
                c = get(ctr);
                suitCount[c.getSuitValue()]++;
            }
            /* Reorganizes the cards to reveal the first suit that has 
             * a straight flush. */
            for (int ctr = 0; ctr < CardDeck.suits.length; ctr++){
                if (suitCount[ctr] >= 5){
                    for (int ctr2 = 0; ctr2 < getSize(); ctr2++){
                        if (get(ctr2).getSuitValue() != ctr){
                            nonFlushCards.add(get(ctr2));
                            remove(ctr2--);
                        }
                    }
                    if (hasStraight()){
                        addAll(nonFlushCards);
                        nonFlushCards.clear();
                        return true;
                    } else {
                        addAll(nonFlushCards);
                        nonFlushCards.clear();
                    }
                }
            }
            // Re-sorts the hand in descending order if no straight flush is found
            Collections.sort(getAllCards());
            Collections.reverse(getAllCards());
            return false;
        }
    }

    /**
     * Extends the Player class for players playing Poker games.
     * @author Yizhe Shen
     */
    private class PokerPlayer extends Player implements Comparable<PokerPlayer> {
        /** The player's cards. */
        private Hand hand;
        /** The player's cards plus any community cards. */
        private PokerHand pHand;

        /**
         * Creates a new PokerPlayer.
         * Creates the new player with the specified parameters.
         * 
         * @param nick IRC user's nick.
         * @param hostmask IRC user's hostmask.
         */
        public PokerPlayer(String nick, String hostmask){
            super(nick, hostmask);
            set("bet", 0);
            set("change", 0);
            set("fold", 0);
            set("allin", 0);
            set("winprob", 0);
            hand = new Hand();
            pHand = new PokerHand();
        }

        /**
         * Returns whether the player has cards in his Hand.
         * @return true if player has any cards
         */
        protected boolean hasHand(){
            return (hand.getSize() > 0);
        }

        /**
         * Returns the player's PokerHand.
         * This includes the player's Hand and any community cards.
         * @return the player's PokerHand
         */
        protected PokerHand getPokerHand(){
            return pHand;
        }

        /**
         * Returns the player's Hand.
         * @return the player's Hand.
         */
        protected Hand getHand(){
            return hand;
        }

        /**
         * Clears the cards in the player's Hand and PokerHand.
         */
        protected void resetHand(){
            hand.clear();
            pHand.clear();
            pHand.resetValue();
        }

        /**
         * Compares this PokerPlayer to another based on their PokerHand.
         * Returns the same value as a comparison of the players' PokerHands.
         * @param p the PokerPlayer to compare
         * @return the result of the comparison of the players' PokerHands
         */
        @Override
        public int compareTo(PokerPlayer p){
            return this.getPokerHand().compareTo(p.getPokerHand());
        }
    }
    
    /**
     * Game simulator for calculating winning percentages.
     * @author Yizhe Shen
     */
    private class PokerSimulator {
        private CardDeck simDeck;
        private ArrayList<PokerPlayer> simList;
        private Hand simComm;
        private int rounds;
        
        public PokerSimulator(ArrayList<PokerPlayer> list, Hand comm) {
            simDeck = new CardDeck(1);
            simList = new ArrayList<PokerPlayer>();
            simComm = new Hand();
            rounds = 0;
            
            PokerPlayer simP;

            // Give simulated players simulated cards matching the real hands.
            for (PokerPlayer p : list) {
                if (!p.has("fold")) {
                    simP = new PokerPlayer(p.getNick(), p.getHost());
                    for (Card aCard : p.getHand().getAllCards()){
                       simP.getHand().add(simDeck.takeCard(aCard));
                       simP.set("wins", 0);
                       simP.set("splits", 0);
                    }
                    simList.add(simP);
                }
            }

            // Give simulated community cards matching the real community.
            for (Card aCard : comm.getAllCards()) {
                simComm.add(simDeck.takeCard(aCard));
            }
            
            // Run simulation
            simulate(0);
        }

        /**
         * Simulates games by sequentially adding cards to simComm until full
         * and determining winners and ties. Uses a recursive algorithm.
         * The number of rounds generated will be nCr, where n is the number of
         * cards remaining in the simDeck and r is the number of cards required
         * to fill simComm.
         * @param index the index in simDeck from which to start adding cards
         *              to simComm
         */
        private void simulate(int index) {
            Card c;
            // Add another card if the simulated community isn't full
            if (simComm.getSize() < 5) {
                for (int ctr = index; ctr < simDeck.getNumberCards(); ctr++) {
                    c = simDeck.peekCard(ctr);
                    simComm.add(c);
                    simulate(ctr + 1);
                    simComm.remove(c);
                }
            } else {
                rounds++;
                int winners = 1;
                
                // System.out.println(simComm);
                
                // Create PokerHands
                for (PokerPlayer p : simList) {
                    p.getPokerHand().addAll(p.getHand());
                    p.getPokerHand().addAll(simComm);
                    Collections.sort(p.getPokerHand().getAllCards());
                    Collections.reverse(p.getPokerHand().getAllCards());
                }
                
                // Sort players by PokerHand
                Collections.sort(simList);
                Collections.reverse(simList);
                
                // Determine number of winners
                for (int ctr = 1; ctr < simList.size(); ctr++){
                    if (simList.get(0).compareTo(simList.get(ctr)) == 0){
                        winners++;
                    }
                }
                
                // Increment win count for winners
                if (winners == 1) {
                    simList.get(0).increment("wins");
                } else {
                    for (int ctr = 0; ctr < winners; ctr++){
                        simList.get(ctr).increment("splits");
                    }
                }
                
                // Clear PokerHands
                for (PokerPlayer p : simList) {
                    p.getPokerHand().clear();
                    p.getPokerHand().resetValue();
                }
            }
        }

        /**
         * Produces a String that contains every player's cards and their
         * percentages for wins and splits.
         * @return a ready-to-display String
         */
        @Override
        public String toString() {
            String out = "Showdown: ";
            for (PokerPlayer p : simList) {
                //out += p.getNickStr() + " (" + p.getHand() + ", " + p.get("wins") +  "/" + p.get("splits") + "/" + rounds + "), ";
                out += p.getNick() + " (" + p.getHand() + ", " + Math.round((double) p.get("wins") / (double) rounds * 100) + "%%, " + Math.round((double) p.get("splits") / (double) rounds * 100) + "%%), ";
            }
            return out.substring(0, out.length() - 2);
        }
    }
    
    /**
     * The default constructor for TexasPoker, subclass of CardGame.
     * This constructor loads the default INI file.
     * 
     * @param parent The bot that uses an instance of this class
     * @param commChar The command char
     * @param gameChannel The IRC channel in which the game is to be run.
     */
    public TexasPoker(CasinoBot parent, char commChar, Channel gameChannel){
        this(parent, commChar, gameChannel, "texaspoker.ini");
    }
    
    /**
     * Allows a custom INI file to be loaded.
     * 
     * @param parent The bot that uses an instance of this class
     * @param commChar The command char
     * @param gameChannel The IRC channel in which the game is to be run.
     * @param customINI the file path to a custom INI file
     */
    public TexasPoker(CasinoBot parent, char commChar, Channel gameChannel, String customINI) {
        super(parent, commChar, gameChannel);
        name = "texaspoker";
        iniFile = customINI;
        helpFile = "texaspoker.help";
        strFile = "strlib.txt";
        loadLib(helpMap, helpFile);
        loadLib(msgMap, strFile);
        house = new HouseStat();
        loadGameStats();
        initialize();
        loadIni();
        deck = new CardDeck();
        deck.shuffleCards();
        pots = new ArrayList<PokerPot>();
        community = new Hand();
        currentPot = null;
        dealer = null;
        smallBlind = null;
        bigBlind = null;
        topBettor = null;
        showMsg(getMsg("game_start"), getGameNameStr());
    }
    
    /* Command management method */
    @Override
    public void processCommand(User user, String command, String[] params){
        String nick = user.getNick();
        String host = user.getHostmask();
        
        /* Check if it's a common command */
        super.processCommand(user, command, params);
        
        /* Parsing commands from the channel */
        if (command.equals("start") || command.equals("go")) {
            if (isStartAllowed(nick)){
                if (params.length > 0){
                    try {
                        set("startcount", Math.min(get("autostarts") - 1, Integer.parseInt(params[0]) - 1));
                    } catch (NumberFormatException e) {
                        // Do nothing and proceed
                    }
                }
                set("inprogress", 1);
                showStartRound();
                setStartRoundTask();
            }
        } else if (command.equals("bet") || command.equals("b")) {
            if (isPlayerTurn(nick)){
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
        } else if (command.equals("c") || command.equals("ca") || command.equals("call")) {
            if (isPlayerTurn(nick)){
                call();
            }
        } else if (command.equals("x") || command.equals("ch") || command.equals("check")) {
            if (isPlayerTurn(nick)){
                check();
            }
        } else if (command.equals("fold") || command.equals("f")) {
            if (isPlayerTurn(nick)){
                fold();
            }
        } else if (command.equals("raise") || command.equals("r")) {
            if (isPlayerTurn(nick)){
                if (params.length > 0){
                    try {
                        bet(Integer.parseInt(params[0]) + get("currentbet"));
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equals("allin") || command.equals("a")){
            if (isPlayerTurn(nick)){
                bet(currentPlayer.get("cash"));
            }
        } else if (command.equals("community") || command.equals("comm")){
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else if (!has("inprogress")) {
                informPlayer(nick, getMsg("no_start"));
            } else if (!has("stage")){
                informPlayer(nick, getMsg("no_community"));
            } else {
                showCommunityCards();
            }
        } else if (command.equals("hand")) {
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else if (!has("inprogress")) {
                informPlayer(nick, getMsg("no_start"));
            } else {
                PokerPlayer p = (PokerPlayer) findJoined(nick);
                informPlayer(p.getNick(), getMsg("tp_hand"), p.getHand());
            }
        } else if (command.equals("turn")) {
            if (!isJoined(nick)) {
                informPlayer(nick, getMsg("no_join"));
            } else if (!has("inprogress")) {
                informPlayer(nick, getMsg("no_start"));
            } else {
                showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), currentPlayer.get("bet"), 
                        currentPlayer.get("cash")-currentPlayer.get("bet"), get("currentbet"));
            }
        } else if (command.equals("players")) {
            if (has("inprogress")){
                showTablePlayers();
            } else {
                showMsg(getMsg("players"), getPlayerListString(joined));
            }
        /* Op commands */
        } else if (command.equals("fstart") || command.equals("fgo")){
            if (isForceStartAllowed(user,nick)){
                set("inprogress", 1);
                showStartRound();
                setStartRoundTask();
            }
        } else if (command.equals("fstop")){
            // Use only as last resort. Data will be lost.
            if (isForceStopAllowed(user,nick)){
                PokerPlayer p;
                cancelIdleOutTask();
                for (int ctr = 0; ctr < joined.size(); ctr++) {
                    p = (PokerPlayer) joined.get(ctr);
                    resetPlayer(p);
                }
                resetGame();
                showMsg(getMsg("end_round"), getGameNameStr(), commandChar);
                set("inprogress", 0);
            }
        } else if (command.equals("fb") || command.equals("fbet")){
            if (isForcePlayAllowed(user, nick)){
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
        } else if (command.equals("fa") || command.equals("fallin")){
            if (isForcePlayAllowed(user, nick)){
                bet(currentPlayer.get("cash"));
            }
        } else if (command.equals("fr") || command.equals("fraise")){
            if (isForcePlayAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        bet(Integer.parseInt(params[0]) + get("currentbet"));
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        } else if (command.equals("fc") || command.equals("fca") || command.equals("fcall")){
            if (isForcePlayAllowed(user, nick)){
                call();
            }
        } else if (command.equals("fx") || command.equals("fch") || command.equals("fcheck")){
            if (isForcePlayAllowed(user, nick)){
                check();
            }
        } else if (command.equals("ff") || command.equals("ffold")){
            if (isForcePlayAllowed(user, nick)){
                fold();
            }
        } else if (command.equals("shuffle")){
            if (isOpCommandAllowed(user, nick)){
                shuffleDeck();
            }
        } else if (command.equals("reload")) {
            if (isOpCommandAllowed(user, nick)){
                loadIni();
                loadLib(helpMap, helpFile);
                loadLib(msgMap, strFile);
                showMsg(getMsg("reload"));
            }
        } else if (command.equals("test1")){
            // 1. Test if game will properly determine winner of 2-5 players
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        ArrayList<PokerPlayer> peeps = new ArrayList<PokerPlayer>();
                        PokerPlayer p;
                        PokerHand ph;
                        int winners = 1;
                        int number = Integer.parseInt(params[0]);
                        if (number > 5 || number < 2){
                            throw new NumberFormatException();
                        }
                        // Generate new players
                        for (int ctr=0; ctr < number; ctr++){
                            p = new PokerPlayer(ctr+1+"", "");
                            peeps.add(p);
                            dealHand(p);                            
                            showMsg("Player "+p.getNickStr()+": "+p.getHand().toString());
                        }
                        // Generate community cards
                        Hand comm = new Hand();
                        for (int ctr=0; ctr<5; ctr++){
                            dealCard(comm);
                        }
                        showMsg("Community: " + comm.toString());
                        // Propagate poker hands
                        for (int ctr=0; ctr < number; ctr++){
                            p = peeps.get(ctr);
                            ph = p.getPokerHand();
                            ph.addAll(p.getHand());
                            ph.addAll(comm);
                            Collections.sort(ph.getAllCards());
                            Collections.reverse(ph.getAllCards());
                            ph.getValue();
                        }
                        // Sort hands in descending order
                        Collections.sort(peeps);
                        Collections.reverse(peeps);
                        // Determine number of winners
                        for (int ctr=1; ctr < peeps.size(); ctr++){
                            if (peeps.get(0).compareTo(peeps.get(ctr)) == 0){
                                winners++;
                            } else {
                                break;
                            }
                        }
                        // Output poker hands with winners listed
                        for (int ctr=0; ctr < winners; ctr++){
                            p = peeps.get(ctr);
                            ph = p.getPokerHand();
                            showMsg("Player "+p.getNickStr()+
                                    " ("+p.getHand()+"), "+ph.getName()+": " + ph+" (WINNER)");
                        }
                        for (int ctr=winners; ctr < peeps.size(); ctr++){
                            p = peeps.get(ctr);
                            ph = p.getPokerHand();
                            showMsg("Player "+p.getNickStr()+
                                    " ("+p.getHand()+"), "+ph.getName()+": " + ph);
                        }
                        // Discard and shuffle
                        for (int ctr=0; ctr < number; ctr++){
                            resetPlayer(peeps.get(ctr));
                        }
                        deck.addToDiscard(comm.getAllCards());
                        comm.clear();
                        deck.refillDeck();
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }                
            }
        } else if (command.equals("test2")){	
            // 2. Test if arbitrary hands will be scored properly
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        int number = Integer.parseInt(params[0]);
                        if (number > 52 || number < 5){
                            throw new NumberFormatException();
                        }
                        PokerHand h = new PokerHand();
                        for (int ctr = 0; ctr < number; ctr++){
                            dealCard(h);
                        }
                        showMsg(h.toString(0, h.getSize()));
                        Collections.sort(h.getAllCards());
                        Collections.reverse(h.getAllCards());
                        h.getValue();
                        showMsg(h.getName()+": " + h);
                        deck.addToDiscard(h.getAllCards());
                        h.clear();
                        deck.refillDeck();
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
                
            }
        } else if (command.equals("test3")){
            // 3. Tests the percentage calculator for 2-5 players
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        int number = Integer.parseInt(params[0]);
                        if (number > 5 || number < 2){
                            throw new NumberFormatException();
                        }
                        ArrayList<PokerPlayer> peeps = new ArrayList<PokerPlayer>();
                        PokerPlayer p;
                        Hand comm = new Hand();
                        PokerSimulator sim;

                        // Generate players and deal cards
                        for (int ctr = 0; ctr < number; ctr++) {
                            p = new PokerPlayer(ctr + "", ctr + "");
                            peeps.add(p);
                            dealHand(p);
                            showMsg("Player "+p.getNickStr()+": "+p.getHand().toString());
                        }

                        // Calculate percentages
                        sim = new PokerSimulator(peeps, comm);
                        showMsg(sim.toString());

                        // Deal flop
                        for (int ctr = 0; ctr < 3; ctr++){
                            dealCard(comm);
                        }
                        showMsg(formatHeader(" Community Cards: ") + " " + comm.toString());

                        // Recalculate percentages
                        sim = new PokerSimulator(peeps, comm);
                        showMsg(sim.toString());

                        // Deal turn
                        dealCard(comm);
                        showMsg(formatHeader(" Community Cards: ") + " " + comm.toString());

                        // Recalculate percentages
                        sim = new PokerSimulator(peeps, comm);
                        showMsg(sim.toString());

                        // Deal river
                        dealCard(comm);
                        showMsg(formatHeader(" Community Cards: ") + " " + comm.toString());
                        
                        // Recalculate percentages
                        sim = new PokerSimulator(peeps, comm);
                        showMsg(sim.toString());

                        // Discard and shuffle
                        for (int ctr=0; ctr < number; ctr++){
                            resetPlayer(peeps.get(ctr));
                        }
                        deck.addToDiscard(comm.getAllCards());
                        comm.clear();
                        deck.refillDeck();
                    } catch (NumberFormatException e) {
                        informPlayer(nick, getMsg("bad_parameter"));
                    }
                } else {
                    informPlayer(nick, getMsg("no_parameter"));
                }
            }
        }
    }

    /* Game management methods */
    @Override
    public void addPlayer(String nick, String host) {
        addPlayer(new PokerPlayer(nick, host));
    }
    
    @Override
    public void addWaitlistPlayer(String nick, String host) {
        Player p = new PokerPlayer(nick, host);
        waitlist.add(p);
        informPlayer(p.getNick(), getMsg("join_waitlist"));
    }
    
    @Override
    public void startRound() {
        if (joined.size() < 2){
            set("startcount", 0);
            endRound();
        } else {
            setButton();
            showTablePlayers();
            dealTable();
            setBlindBets();
            currentPlayer = getPlayerAfter(bigBlind);
            showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), currentPlayer.get("bet"), 
                        currentPlayer.get("cash")-currentPlayer.get("bet"), get("currentbet"));
            setIdleOutTask();
        }
    }
    
    @Override
    public void continueRound() {
        // Store currentPlayer as firstPlayer and find the next player
        Player firstPlayer = currentPlayer;
        currentPlayer = getPlayerAfter(currentPlayer);
        PokerPlayer p = (PokerPlayer) currentPlayer;
        
        /*
         * Look for a player who can bet that is not the firstPlayer or the
         * topBettor. If we reach the firstPlayer or topBettor then stop
         * looking.
         */
        while ((p.has("fold") || p.has("allin")) && currentPlayer != firstPlayer
                        && currentPlayer != topBettor) {
            currentPlayer = getPlayerAfter(currentPlayer);
            p = (PokerPlayer) currentPlayer;
        }
        
        /* If we reach the firstPlayer or topBettor, then we have reached the
         * end of a round of betting and we should deal community cards. */
        if (currentPlayer == topBettor || currentPlayer == firstPlayer) {
            // Reset minimum raise
            set("minraise", get("minbet"));
            // Add bets from this round of betting to the pot
            addBetsToPot();
            increment("stage");
            
            // If all community cards have been dealt, move to end of round
            if (get("stage") == 4){
                endRound();
            // Otherwise, deal community cards
            } else {
                /* 
                * If fewer than two players can bet and there are more
                * than 1 non-folded player remaining, only show hands once
                * before dealing the rest of the community cards. Adds a
                * 10-second delay in between each line.
                */
                if (getNumberCanBet() < 2 && getNumberNotFolded() > 1) {
                    if (currentPlayer == topBettor){
                        ArrayList<PokerPlayer> players;
                        players = pots.get(0).getPlayers();
                        String showdownStr = formatHeader(" Showdown: ") + " ";
                        for (int ctr = 0; ctr < players.size(); ctr++) {
                            p = players.get(ctr);
                            showdownStr += p.getNickStr() + " (" + p.getHand() + ")";
                            if (ctr < players.size()-1){
                                showdownStr += ", ";
                            }
                        }
                        showMsg(showdownStr);
                    }
                   
                   // Add a delay for dramatic effect
                   try { Thread.sleep(get("showdown") * 1000); } catch (InterruptedException e){}
                }
                // Burn a card before turn and river
                if (get("stage") != 1) {
                    burnCard();
                }
                dealCommunity();
                /* Only show dealt community cards when there are 
                 * more than 1 non-folded player remaining. */
                if (getNumberNotFolded() > 1){
                    showCommunityCards();
                }
                topBettor = null;
                /* Set the currentPlayer to be dealer to determine who bets
                 * first in the next round of betting. */
                currentPlayer = dealer;
                continueRound();
            }
        // Continue round, if less than 2 players can bet
        } else if (getNumberCanBet() < 2 && topBettor == null){
            continueRound();
        // Continue to the next bettor
        } else {
            showMsg(getMsg("tp_turn"), currentPlayer.getNickStr(), currentPlayer.get("bet"), 
                        currentPlayer.get("cash")-currentPlayer.get("bet"), get("currentbet"));
            setIdleOutTask();
        }
    }
    
    @Override
    public void endRound() {
        set("endround", 1);
        PokerPlayer p;

        // Check if anybody left during post-start waiting period
        if (joined.size() > 1 && pots.size() > 0) {
            String netResults = Colors.YELLOW + ",01 Change: ";
            // Give all non-folded players the community cards
            for (int ctr = 0; ctr < joined.size(); ctr++){
                p = (PokerPlayer) joined.get(ctr);
                if (!p.has("fold")){
                    p.getPokerHand().addAll(p.getHand());
                    p.getPokerHand().addAll(community);
                    Collections.sort(p.getPokerHand().getAllCards());
                    Collections.reverse(p.getPokerHand().getAllCards());
                }
            }

            // Determine the winners of each pot
            showResults();

            /* Clean-up tasks
             * 1. Increment the number of rounds played for player
             * 2. Remove players who have gone bankrupt and set respawn timers
             * 3. Remove players who have quit mid-round
             * 4. Save player data
             * 5. Reset the player
             */
            for (int ctr = 0; ctr < joined.size(); ctr++){
                p = (PokerPlayer) joined.get(ctr);
                p.increment("tprounds");
                
                // Bankrupts
                if (!p.has("cash")) {
                    // Make a withdrawal if the player has a positive bank
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
                    // Give penalty to players with no cash in their bank
                    } else {
                        p.increment("bankrupts");
                        blacklist.add(p);
                        removeJoined(p);
                        setRespawnTask(p);
                        ctr--;
                    }
                // Quitters
                } else if (p.has("quit")) {
                    removeJoined(p);
                    ctr--;
                // Remaining players
                } else {
                    savePlayerData(p);
                }
                
                // Add player to list of net results
                if (p.get("change") > 0) {
                     netResults += Colors.WHITE + ",01 " + p.getNick() + " (" + Colors.GREEN + ",01$" + formatNumber(p.get("change")) + Colors.WHITE + ",01), ";
                } else if (p.get("change") < 0) {
                    netResults += Colors.WHITE + ",01 " + p.getNick() + " (" + Colors.RED + ",01$" + formatNumber(p.get("change")) + Colors.WHITE + ",01), ";
                } else {
                    netResults += Colors.WHITE + ",01 " + p.getNick() + " (" + Colors.WHITE + ",01$" + formatNumber(p.get("change")) + Colors.WHITE + ",01), ";
                }
                resetPlayer(p);
            }
            showMsg(netResults.substring(0, netResults.length()-2));
        } else {
            showMsg(getMsg("no_players"));
        }
        resetGame();
        showMsg(getMsg("end_round"), getGameNameStr(), commandChar);
        mergeWaitlist();
        // Check if auto-starts remaining
        if (get("startcount") > 0){
            decrement("startcount");
            if (!has("inprogress")){
                if (joined.size() > 1) {
                    set("inprogress", 1);
                    showStartRound();
                    setStartRoundTask();
                } else {
                    set("startcount", 0);
                }
            }
        }
    }
    
    @Override
    public void endGame() {
        cancelStartRoundTask();
        cancelIdleOutTask();
        cancelRespawnTasks();
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
        house = null;
        devoiceAll();
        showMsg(getMsg("game_end"), getGameNameStr());
        joined.clear();
        waitlist.clear();
        blacklist.clear();
        helpMap.clear();
        msgMap.clear();
        settingsMap.clear();
    }
    
    @Override
    public void resetGame() {
        set("stage", 0);
        set("currentbet", 0);
        set("inprogress", 0);
        set("endround", 0);
        discardCommunity();
        currentPot = null;
        pots.clear();
        currentPlayer = null;
        bigBlind = null;
        smallBlind = null;
        topBettor = null;
        deck.refillDeck();
    }
    
    @Override
    public void leave(String nick) {
        // Check if the nick is even joined
        if (isJoined(nick)){
            PokerPlayer p = (PokerPlayer) findJoined(nick);
            // Check if a round is in progress
            if (has("inprogress")) {
                /* If still in the post-start waiting phase, then currentPlayer has
                 * not been set yet. */
                if (currentPlayer == null){
                    removeJoined(p);
                // Check if it is already in the endRound stage
                } else if (has("endround")){
                    p.set("quit", 1);
                    informPlayer(p.getNick(), getMsg("remove_end_round"));
                // Force the player to fold if it is his turn
                } else if (p == currentPlayer){
                    p.set("quit", 1);
                    informPlayer(p.getNick(), getMsg("remove_end_round"));
                    fold();
                } else {
                    p.set("quit", 1);
                    informPlayer(p.getNick(), getMsg("remove_end_round"));
                    if (!p.has("fold")){
                        p.set("fold", 1);
                        showMsg(getMsg("tp_fold"), p.getNickStr(), p.get("cash")-p.get("bet"));
                        // Remove this player from any existing pots
                        if (currentPot != null && currentPot.hasPlayer(p)){
                            currentPot.removePlayer(p);
                        }
                        for (int ctr = 0; ctr < pots.size(); ctr++){
                            PokerPot cPot = pots.get(ctr);
                            if (cPot.hasPlayer(p)){
                                cPot.removePlayer(p);
                            }
                        }
                        // If there is only one player who hasn't folded,
                        // force call on that remaining player (whose turn it must be)
                        if (getNumberNotFolded() == 1){
                            call();
                        }
                    }
                }
            // Just remove the player from the joined list if no round in progress
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
    
    /**
     * Returns next player after the specified player.
     * @param p the specified player
     * @return the next player
     */
    private Player getPlayerAfter(Player p){
        return joined.get((joined.indexOf(p) + 1) % joined.size());
    }
    
    /**
     * Resets the specified player.
     * @param p the player to reset
     */
    private void resetPlayer(PokerPlayer p) {
        discardPlayerHand(p);
        p.clear("fold");
        p.clear("quit");
        p.clear("allin");
        p.clear("change");
    }
    
    /**
     * Assigns players to the dealer, small blind and big blind roles.
     */
    private void setButton(){
        if (dealer == null){
            dealer = (PokerPlayer) joined.get(0);
        } else {
            dealer = (PokerPlayer) getPlayerAfter(dealer);
        }
        if (joined.size() == 2){
            smallBlind = dealer;
        } else {
            smallBlind = (PokerPlayer) getPlayerAfter(dealer);
        }
        bigBlind = (PokerPlayer) getPlayerAfter(smallBlind);
    }
    
    /**
     * Sets the bets for the small and big blinds.
     */
    private void setBlindBets(){
        // Set the small blind to minimum raise or the player's cash, 
        // whichever is less.
        smallBlind.set("bet", Math.min(get("minbet")/2, smallBlind.get("cash")));
        // Set the big blind to minimum raise + small blind or the player's 
        // cash, whichever is less.
        bigBlind.set("bet", Math.min(get("minbet"), bigBlind.get("cash")));
        // Set the current bet to the bigger of the two blinds.
        set("currentbet", Math.max(smallBlind.get("bet"), bigBlind.get("bet")));
        set("minraise", get("minbet"));
    }
    
    /* Game command logic checking methods */
    public boolean isStartAllowed(String nick){
        if (!isJoined(nick)) {
            informPlayer(nick, getMsg("no_join"));
        } else if (has("inprogress")) {
            informPlayer(nick, getMsg("round_started"));
        } else if (joined.size() < 2) {
            showMsg(getMsg("no_players"));
        } else {
            return true;
        }
        return false;
    }
    public boolean isPlayerTurn(String nick){
        if (!isJoined(nick)){
            informPlayer(nick, getMsg("no_join"));
        } else if (!has("inprogress")) {
            informPlayer(nick, getMsg("no_start"));
        } else if (findJoined(nick) != currentPlayer){
            informPlayer(nick, getMsg("wrong_turn"));
        } else {
            return true;
        }
        return false;
    }
    public boolean isForceStartAllowed(User user, String nick){
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (has("inprogress")) {
            informPlayer(nick, getMsg("round_started"));
        } else if (joined.size() < 2) {
            showMsg(getMsg("no_players"));
        } else {
            return true;
        }
        return false;
    }
    public boolean isForceStopAllowed(User user, String nick){
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!has("inprogress")) {
            informPlayer(nick, getMsg("no_start"));
        } else {
            return true;
        }
        return false;
    }
    public boolean isForcePlayAllowed(User user, String nick){
        if (!channel.isOp(user)) {
            informPlayer(nick, getMsg("ops_only"));
        } else if (!has("inprogress")) {
            informPlayer(nick, getMsg("no_start"));
        } else {
            return true;
        }
        return false;
    }
    
    /* Game settings management */
    @Override
    protected final void initialize(){
        super.initialize();
        // Do not use set()
        // Ini file settings
        settingsMap.put("cash", 1000);
        settingsMap.put("idle", 60);
        settingsMap.put("idlewarning", 45);
        settingsMap.put("respawn", 600);
        settingsMap.put("maxplayers", 22);
        settingsMap.put("minbet", 10);
        settingsMap.put("autostarts", 10);
        settingsMap.put("startwait", 5);
        settingsMap.put("showdown", 10);
        // In-game properties
        settingsMap.put("stage", 0);
        settingsMap.put("currentbet", 0);
        settingsMap.put("minraise", 0);
    }
    
    @Override
    protected final void saveIniFile() {
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(iniFile)));
            out.println("#Settings");
            out.println("#Number of seconds before a player idles out");
            out.println("idle=" + get("idle"));
            out.println("#Number of seconds before a player is given a warning for idling");
            out.println("idlewarning=" + get("idlewarning"));
            out.println("#Initial amount given to new and bankrupt players");
            out.println("cash=" + get("cash"));
            out.println("#Number of seconds before a bankrupt player is allowed to join again");
            out.println("respawn=" + get("respawn"));
            out.println("#Minimum bet (big blind), preferably an even number");
            out.println("minbet=" + get("minbet"));
            out.println("#The maximum number of players allowed to join a game");
            out.println("maxplayers=" + get("maxplayers"));
            out.println("#The maximum number of autostarts allowed");
            out.println("autostarts=" + get("autostarts"));
            out.println("#The wait time in seconds after the start command is given");
            out.println("startwait=" + get("startwait"));
            out.println("#The wait time in seconds in between reveals during a showdown");
            out.println("showdown=" + get("showdown"));
            out.close();
        } catch (IOException e) {
            manager.log("Error creating " + iniFile + "!");
        }
    }
    
    /* House stats management */
    @Override
    public final void loadGameStats() {
        try {
            BufferedReader in = new BufferedReader(new FileReader("housestats.txt"));
            String str;
            int biggestpot, players, winners;
            StringTokenizer st;
            while (in.ready()) {
                str = in.readLine();
                if (str.startsWith("#texaspoker")) {
                    while (in.ready()) {
                        str = in.readLine();
                        if (str.startsWith("#")) {
                            break;
                        }
                        st = new StringTokenizer(str);
                        biggestpot = Integer.parseInt(st.nextToken());
                        house.set("biggestpot", biggestpot);
                        players = Integer.parseInt(st.nextToken());
                        for (int ctr = 0; ctr < players; ctr++) {
                            house.addDonor(new PokerPlayer(st.nextToken(), ""));
                        }
                        winners = Integer.parseInt(st.nextToken());
                        for (int ctr = 0; ctr < winners; ctr++) {
                            house.addWinner(new PokerPlayer(st.nextToken(), ""));
                        }
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
    
    @Override
    public void saveGameStats() {
        boolean found = false;
        int index = 0;
        ArrayList<String> lines = new ArrayList<String>();
        try {
            BufferedReader in = new BufferedReader(new FileReader("housestats.txt"));
            String str;
            while (in.ready()) {
                //Add all lines until we find texaspoker lines
                str = in.readLine();
                lines.add(str);
                if (str.startsWith("#texaspoker")) {
                    found = true;
                    /* Store the index where texaspoker stats go so they can be 
                     * overwritten. */
                    index = lines.size();
                    //Skip existing texaspoker lines but add all the rest
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
            lines.add("#texaspoker");
            index = lines.size();
        }
        lines.add(index, house.get("biggestpot") + " " + house.getNumDonors() + " " + house.getDonorsString() + " " + house.getNumWinners() + " " + house.getWinnersString());
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
    
    /* Card management methods for Texas Hold'em Poker */
    
    /**
     * Deals cards to the community hand.
     */
    private void dealCommunity(){
        if (get("stage") == 1) {
            for (int ctr = 1; ctr <= 3; ctr++){
                dealCard(community);
            }
        } else {
            dealCard(community);
        }
    }
    /**
     * Deals two cards to the specified player.
     * @param p the player to be dealt to
     */
    private void dealHand(PokerPlayer p) {
        dealCard(p.getHand());
        dealCard(p.getHand());
    }
    /**
     * Deals hands to everybody at the table.
     */
    private void dealTable() {
        PokerPlayer p;
        for (int ctr = 0; ctr < joined.size(); ctr++) {
            p = (PokerPlayer) joined.get(ctr);
            dealHand(p);
            informPlayer(p.getNick(), getMsg("tp_hand"), p.getHand());
        }
    }
    /**
     * Discards a player's hand into the discard pile.
     * @param p the player whose hand is to be discarded
     */
    private void discardPlayerHand(PokerPlayer p) {
        if (p.hasHand()) {
            deck.addToDiscard(p.getHand().getAllCards());
            p.resetHand();
        }
    }
    /**
     * Discards the community cards into the discard pile.
     */
    private void discardCommunity(){
        if (community.getSize() > 0){
            deck.addToDiscard(community.getAllCards());
            community.clear();
        }
    }
    /**
     * Merges the discards and shuffles the deck.
     */
    private void shuffleDeck() {
        deck.refillDeck();
        showMsg(getMsg("tp_shuffle_deck"));
    }
    
    /* Texas Hold'em Poker gameplay methods */
    
    /**
     * Processes a bet command.
     * @param amount the amount to bet
     */
    public void bet (int amount) {
        cancelIdleOutTask();
        PokerPlayer p = (PokerPlayer) currentPlayer;
        
        // A bet that's an all-in
        if (amount == p.get("cash")){
            if (amount > get("currentbet") || topBettor == null){
                if (amount - get("currentbet") > get("minraise")){
                    set("minraise", amount - get("currentbet"));
                }
                set("currentbet", amount);
                topBettor = p;
            }
            p.set("bet", amount);
            p.set("allin", 1);
            showMsg(getMsg("tp_allin"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
            continueRound();
        // A bet that's larger than a player's stack
        } else if (amount > p.get("cash")) {
            informPlayer(p.getNick(), getMsg("insufficient_funds"));
            setIdleOutTask();
        // A bet that's lower than the current bet
        } else if (amount < get("currentbet")) {
            informPlayer(p.getNick(), getMsg("tp_bet_too_low"), get("currentbet"));
            setIdleOutTask();
        // A bet that's equivalent to a call or check
        } else if (amount == get("currentbet")){
            if (topBettor == null){
                topBettor = p;
            }
            if (amount == 0 || p.get("bet") == amount){
                showMsg(getMsg("tp_check"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
            } else {
                p.set("bet", amount);
                showMsg(getMsg("tp_call"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
            }
            continueRound();
        // A bet that's lower than the minimum raise
        } else if (amount - get("currentbet") < get("minraise")){
            informPlayer(p.getNick(), getMsg("raise_too_low"), get("minraise"));
            setIdleOutTask();
        // A valid bet that's greater than the currentBet
        } else {
            p.set("bet", amount);
            topBettor = p;
            if (get("currentbet") == 0){
                showMsg(getMsg("tp_bet"), p.getNickStr(), p.get("bet"), p.get("cash") - p.get("bet"));
            } else {
                showMsg(getMsg("tp_raise"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
            }
            set("minraise", amount - get("currentbet"));
            set("currentbet", amount);
            continueRound();
        }
    }
    
    /**
     * Processes a check command.
     * Only allow a player to check when the currentBet is 0 or if the player
     * has already committed the required amount to pot.
     */
    public void check(){
        cancelIdleOutTask();
        PokerPlayer p = (PokerPlayer) currentPlayer;
        
        if (get("currentbet") == 0 || p.get("bet") == get("currentbet")){
            if (topBettor == null){
                topBettor = p;
            }
            showMsg(getMsg("tp_check"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
            continueRound();
        } else {
            informPlayer(p.getNick(), getMsg("no_checking"), get("currentbet"));
            setIdleOutTask();
        }
    }
    
    /**
     * Processes a call command.
     * A player's bet will be matched to the currentBet. If a player's stack
     * is less than the currentBet, the player will move all-in.
     */
    public void call(){
        cancelIdleOutTask();
        PokerPlayer p = (PokerPlayer) currentPlayer;
        int total = Math.min(p.get("cash"), get("currentbet"));
        
        if (topBettor == null){
            topBettor = p;
        }
        
        // A call that's an all-in to match the currentBet
        if (total == p.get("cash")){
            p.set("allin", 1);
            p.set("bet", total);
            showMsg(getMsg("tp_allin"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
        // A check
        } else if (total == 0 || p.get("bet") == total){
            showMsg(getMsg("tp_check"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
        // A call
        } else {
            p.set("bet", total);
            showMsg(getMsg("tp_call"), p.getNickStr(), p.get("bet"), p.get("cash")-p.get("bet"));
        }
        continueRound();
    }
    
    /**
     * Process a fold command.
     * The folding player is removed from all pots.
     */
    public void fold(){
        cancelIdleOutTask();
        PokerPlayer p = (PokerPlayer) currentPlayer;
        p.set("fold", 1);
        showMsg(getMsg("tp_fold"), p.getNickStr(), p.get("cash")-p.get("bet"));

        //Remove this player from any existing pots
        if (currentPot != null && currentPot.hasPlayer(p)){
            currentPot.removePlayer(p);
        }
        for (int ctr = 0; ctr < pots.size(); ctr++){
            PokerPot cPot = pots.get(ctr);
            if (cPot.hasPlayer(p)){
                cPot.removePlayer(p);
            }
        }
        continueRound();
    }
    
    /* Behind the scenes methods */
    
    /**
     * Determines the number of players who have not folded.
     * @return the number of non-folded players
     */
    private int getNumberNotFolded(){
        PokerPlayer p;
        int numberNotFolded = 0;
        for (int ctr = 0; ctr < joined.size(); ctr++){
            p = (PokerPlayer) joined.get(ctr);
            if (!p.has("fold")){
                numberNotFolded++;
            }
        }
        return numberNotFolded;
    }
    
    /**
     * Determines the number players who can still make a bet.
     * @return the number of players who can bet
     */
    private int getNumberCanBet(){
        PokerPlayer p;
        int numberCanBet = 0;
        for (int ctr = 0; ctr < joined.size(); ctr++){
            p = (PokerPlayer) joined.get(ctr);
            if (!p.has("fold") && !p.has("allin")){
                numberCanBet++;
            }
        }
        return numberCanBet;
    }
    
    /**
     * Determines the number of players who have made a bet in a round of 
     * betting.
     * @return the number of bettors
     */
    private int getNumberBettors() {
        PokerPlayer p;
        int numberBettors = 0;
        for (int ctr = 0; ctr < joined.size(); ctr++){
            p = (PokerPlayer) joined.get(ctr);
            if (p.has("bet")){
                numberBettors++;
            }
        }
        return numberBettors;
    }
    
    /**
     * Adds the bets during a round of betting to the pot.
     * If no pot exists, a new one is created. Sidepots are created as necessary.
     */
    private void addBetsToPot(){
        PokerPlayer p;
        int lowBet;
        while(get("currentbet") != 0){
            lowBet = get("currentbet");
            // Only add bets to a pot if more than one player has made a bet
            if (getNumberBettors() > 1) {
                // Create a new pot if currentPot is set to null
                if (currentPot == null){
                    currentPot = new PokerPot();
                    pots.add(currentPot);
                } else {
                    // Determine if anybody in the current pot has no more bet
                    // left to contribute but is still in the game. If so, 
                    // then a new pot will be required.
                    for (int ctr = 0; ctr < currentPot.getNumPlayers(); ctr++) {
                        p = currentPot.getPlayer(ctr);
                        if (!p.has("bet") && get("currentbet") != 0 && !p.has("fold") && currentPot.hasPlayer(p)) {
                            currentPot = new PokerPot();
                            pots.add(currentPot);
                            break;
                        }
                    }
                }

                // Determine the lowest non-zero bet
                for (int ctr = 0; ctr < joined.size(); ctr++) {
                    p = (PokerPlayer) joined.get(ctr);
                    if (p.get("bet") < lowBet && p.has("bet")){
                        lowBet = p.get("bet");
                    }
                }
                // Subtract lowBet from each player's (non-zero) bet and add to pot.
                for (int ctr = 0; ctr < joined.size(); ctr++){
                    p = (PokerPlayer) joined.get(ctr);
                    if (p.has("bet")){
                        // Check if player has been added to donor list
                        if (!currentPot.hasDonor(p)) {
                            currentPot.addDonor(p);
                        }
                        // Ensure a non-folded player is included in this pot
                        if (!p.has("fold") && !currentPot.hasPlayer(p)){
                            currentPot.addPlayer(p);
                        }
                        // Transfer lowBet from the player to the pot
                        currentPot.add(lowBet);
                        p.add("cash", -1 * lowBet);
                        p.add("tpwinnings", -1 * lowBet);
                        p.add("bet", -1 * lowBet);
                        p.add("change", -1 * lowBet);
                    }
                }
                // Update currentbet
                set("currentbet", get("currentbet") - lowBet);
            
            // If only one player has any bet left then it should not be
            // contributed to the current pot and his bet and currentBet should
            // be reset.
            } else {
                for (int ctr = 0; ctr < joined.size(); ctr++){
                    p = (PokerPlayer) joined.get(ctr);
                    if (p.get("bet") != 0){
                        p.clear("bet");
                        break;
                    }
                }
                set("currentbet", 0);
                break;
            }
        }
    }
    
    @Override
    public int getTotalPlayers(){
        try {
            ArrayList<StatFileLine> statList = new ArrayList<StatFileLine>();
            loadPlayerFile(statList);
            int total = 0, numLines = statList.size();
            
            for (int ctr = 0; ctr < numLines; ctr++){
                if (statList.get(ctr).has("tprounds")){
                    total++;
                }
            }
            return total;
        } catch (IOException e){
            manager.log("Error reading players.txt!");
            return 0;
        }
    }    
    
    /* Channel message output methods for Texas Hold'em Poker*/
    
    /**
     * Displays the players who are involved in a round. Players who have not
     * folded are displayed in bold. Designations for small blind, big blind,
     * and dealer are also shown.
     */
    public void showTablePlayers(){
        PokerPlayer p;
        String msg = formatBold(joined.size()) + " players: ";
        String nickColor;
        for (int ctr = 0; ctr < joined.size(); ctr++){
            p = (PokerPlayer) joined.get(ctr);
            // Give bold to remaining non-folded players
            if (!p.has("fold")){
                nickColor = Colors.BOLD;
            } else {
                nickColor = "";
            }
            msg += nickColor+p.getNick();

            // Give special players a label
            if (p == dealer || p == smallBlind || p == bigBlind){
                msg += "(";
                if (p == dealer){
                    msg += "D";
                }
                if (p == smallBlind){
                    msg += "S";
                } else if (p == bigBlind){
                    msg += "B";
                }
                msg += ")";
            }
            msg += nickColor;
            if (ctr != joined.size() - 1){
                msg += ", ";
            }
        }
        showMsg(msg);
    }
    
    /**
     * Displays the community cards along with existing pots.
     */
    public void showCommunityCards(){
        PokerPlayer p;
        StringBuilder msg = new StringBuilder();
        // Append community cards to StringBuilder
        String str = formatHeader(" Community Cards: ") + " " + community.toString() + " ";
        msg.append(str);
        
        // Append existing pots to StringBuilder
        for (int ctr = 0; ctr < pots.size(); ctr++){
            str = Colors.YELLOW+",01Pot #"+(ctr+1)+": "+Colors.GREEN+",01$"+formatNumber(pots.get(ctr).getTotal())+Colors.NORMAL+" ";
            msg.append(str);
        }
        
        // Append remaining non-folded players
        int notFolded = getNumberNotFolded();
        int count = 0;
        str = "(" + formatBold(notFolded) + " players: ";
        for (int ctr = 0; ctr < joined.size(); ctr++){
            p = (PokerPlayer) joined.get(ctr);
            if (!p.has("fold")){
                str += p.getNick();
                if (count != notFolded-1){
                    str += ", ";
                }
                count++;
            }
        }
        str += ")";
        msg.append(str);
        
        showMsg(msg.toString());
    }

    /**
     * Displays the results of a round.
     */
    public void showResults(){
        ArrayList<PokerPlayer> players;
        PokerPlayer p;
        int winners;
        // Show introduction to end results
        showMsg(formatHeader(" Results: "));
        players = pots.get(0).getPlayers();
        Collections.sort(players);
        Collections.reverse(players);
        // Show each remaining player's hand
        if (pots.get(0).getNumPlayers() > 1){
            for (int ctr = 0; ctr < players.size(); ctr++){
                p = players.get(ctr);
                showMsg(getMsg("tp_player_result"), p.getNickStr(), p.getHand(), p.getPokerHand().getName(), p.getPokerHand());
            }
        }
        // Find the winner(s) from each pot
        for (int ctr = 0; ctr < pots.size(); ctr++){
            winners = 1;
            currentPot = pots.get(ctr);
            players = currentPot.getPlayers();
            Collections.sort(players);
            Collections.reverse(players);
            // Determine number of winners
            for (int ctr2=1; ctr2 < currentPot.getNumPlayers(); ctr2++){
                if (players.get(0).compareTo(players.get(ctr2)) == 0){
                    winners++;
                }
            }
            
            // Output winners
            for (int ctr2=0; ctr2<winners; ctr2++){
                p = players.get(ctr2);
                p.add("cash", currentPot.getTotal()/winners);
                p.add("tpwinnings", currentPot.getTotal()/winners);
                p.add("change", currentPot.getTotal()/winners);
                showMsg(Colors.YELLOW+",01 Pot #" + (ctr+1) + ": " + Colors.NORMAL + " " + 
                    p.getNickStr() + " wins $" + formatNumber(currentPot.getTotal()/winners) + 
                    ". Stack: $" + formatNumber(p.get("cash"))+ " (" + getPlayerListString(currentPot.getPlayers()) + ")");
            }
            
            // Check if it's the biggest pot
            if (house.get("biggestpot") < currentPot.getTotal()){
                house.set("biggestpot", currentPot.getTotal());
                house.clearDonors();
                house.clearWinners();
                // Store the list of donors
                for (int ctr2 = 0; ctr2 < currentPot.getNumDonors(); ctr2++){
                    house.addDonor(new PokerPlayer(currentPot.getDonor(ctr2).getNick(), ""));
                }
                // Store the list of winners
                for (int ctr2 = 0; ctr2 < winners; ctr2++){
                    house.addWinner(new PokerPlayer(currentPot.getPlayer(ctr2).getNick(), ""));
                }
                saveGameStats();
            }
        }
    }
    
    @Override
    public void showPlayerWinnings(String nick){
        int winnings = getPlayerStat(nick, "tpwinnings");
        if (winnings != Integer.MIN_VALUE) {
            showMsg(getMsg("player_winnings"), formatNoPing(nick), winnings, getGameNameStr());
        } else {
            showMsg(getMsg("no_data"), formatNoPing(nick));
        }
    }
    
    @Override
    public void showPlayerWinRate(String nick){
        double winnings = (double) getPlayerStat(nick, "tpwinnings");
        double rounds = (double) getPlayerStat(nick, "tprounds");
        
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
        int rounds = getPlayerStat(nick, "tprounds");
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
        int winnings = getPlayerStat(nick, "tpwinnings");
        int rounds = getPlayerStat(nick, "tprounds");
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
                    test.add((double) statList.get(ctr).get("tpwinnings"));
                }
                line += "Texas Hold'em Winnings: ";
            } else if (stat.equals("rounds")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("tprounds"));
                }
                line += "Texas Hold'em Rounds: ";
            } else if (stat.equals("winrate")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    if (statList.get(ctr).get("tprounds") == 0){
                        test.add(0.);
                    } else {
                        test.add((double) statList.get(ctr).get("tpwinnings") / (double) statList.get(ctr).get("tprounds"));
                    }
                }
                line += "Texas Hold'em Win Rate: ";
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
                    test.add((double) statList.get(ctr).get("tpwinnings"));
                }
                title += " Texas Hold'em Winnings ";
            } else if (stat.equals("rounds")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    test.add((double) statList.get(ctr).get("tprounds"));
                }
                title += " Texas Hold'em Rounds ";
            } else if (stat.equals("winrate")) {
                for (int ctr = 0; ctr < statList.size(); ctr++) {
                    if (statList.get(ctr).get("tprounds") == 0){
                        test.add(0.);
                    } else {
                        test.add((double) statList.get(ctr).get("tpwinnings") / (double) statList.get(ctr).get("tprounds"));
                    }
                }
                title += " Texas Hold'em Win Rate ";
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
    public final String getGameNameStr() {
        return formatBold(getMsg("tp_game_name"));
    }
    
    @Override
    public final String getGameRulesStr() {
        return String.format(getMsg("tp_rules"), get("minbet")/2, get("minbet"));
    }
    
    @Override
    public final String getGameStatsStr() {
        return String.format(getMsg("tp_stats"), getTotalPlayers(), getGameNameStr(), house);
    }
}
