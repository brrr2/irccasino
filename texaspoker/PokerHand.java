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

package irccasino.texaspoker;

import irccasino.cardgame.Card;
import irccasino.cardgame.CardDeck;
import irccasino.cardgame.Hand;
import java.util.Collections;

/**
 * Extends Hand with extra methods for Poker hand comparisons.
 * @author Yizhe Shen
 */
public class PokerHand extends Hand implements Comparable<PokerHand>{
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
                comps[ctr] = get(ctr).getFaceValue() - h.get(ctr).getFaceValue();
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
    public int getValue(){
        if (value == -1){
            value = calcValue();
        }
        return value;
    }

    /**
     * Resets the value to the default.
     */
    public void resetValue(){
        value = -1;
    }

    /**
     * Name of the hand.
     * @return the name of the hand based on value.
     */
    public String getName(){
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
            // Royal/straight flush, full house, flush, straight, high card
            case 9: case 8: case 6: case 5: case 4: case 0: 
                out = toString(0,5);
                break;
            case 7: case 2: // 4 of a kind, 2 pairs
                out = toString(0,4)+"/"+toString(4,5);
                break;
            case 3: // 3 of a kind
                out = toString(0,3)+"/"+toString(3,5);
                break;
            case 1: // 1 pair
                out = toString(0,2)+"/"+toString(2,5);
                break;
            default:
                out = "";
        }
        if (size() > 5){
            out += "||" + toString(5, size());
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
     * @return value corresponding to hand-type.
     */
    public int calcValue(){
        // Always check the hands in order of descending value
        if (hasStraightFlush()){	
            if (get(0).isFace("A")){
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
     * @return true if it contains a pair
     */
    public boolean hasPair(){
        Card a,b;
        for (int ctr = 0; ctr < size()-1; ctr++){
            a = get(ctr);
            b = get(ctr+1);
            if (a.sameFace(b)){
                remove(a);
                remove(b);
                add(0, a);
                add(1, b);
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if a PokerHand has two pairs.
     * @return true if it contains two pairs
     */
    public boolean hasTwoPair(){
        Card a,b;
        for (int ctr = 0; ctr < size()-3; ctr++){
            a = get(ctr);
            b = get(ctr+1);
            if (a.sameFace(b)){
                remove(a);
                remove(b);
                if (hasPair()){
                    add(0, a);
                    add(1, b);
                    return true;
                } else {
                    add(ctr, a);
                    add(ctr+1, b);
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Determines if a PokerHand has three of a kind.
     * @return true if it contains three of a kind
     */
    public boolean hasThreeOfAKind(){
        Card a,b,c;
        for (int ctr = 0; ctr < size()-2; ctr++){
            a = get(ctr);
            b = get(ctr+1);
            c = get(ctr+2);
            if (a.sameFace(b) && b.sameFace(c)){
                remove(a);
                remove(b);
                remove(c);
                add(0, a);
                add(1, b);
                add(2, c);
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if a PokerHand has a straight.
     * @return true if it contains a straight
     */
    public boolean hasStraight(){
        /* Create a boolean array to determine which face cards exist in the hand.
         * An extra index is added at the beginning for the value duality of aces.
         */
        boolean[] cardValues = new boolean[CardDeck.faces.length+1];
        for (Card c : this) {
            if (c.isFace("A")){
                cardValues[0] = true;
            }
            cardValues[c.getFaceValue()+1] = true;
        }
        Card c;
        // Determine if any sequence of 5 consecutive cards exist
        for (int ctr = cardValues.length-1; ctr >= 4; ctr--){
            if (cardValues[ctr] && cardValues[ctr-1] && cardValues[ctr-2] && 
                cardValues[ctr-3] && cardValues[ctr-4]){
                // Move the sequence in descending order to the beginning of the hand
                for (int ctr2 = 0; ctr2 < 5; ctr2++){
                    for (int ctr3 = 0; ctr3 < size(); ctr3++){
                        c = get(ctr3);
                        if ((ctr-ctr2 == 0 && c.getFace().equals("A")) || c.getFaceValue()+1 == ctr-ctr2){
                            remove(c);
                            add(ctr2, c);
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
     * @return true if it contains a flush
     */
    public boolean hasFlush(){
        int[] suitCount = new int[CardDeck.suits.length];
        for (Card c : this){
            suitCount[c.getSuitValue()]++;
        }
        Card c;
        for (int ctr = 0; ctr < suitCount.length; ctr++){
            if (suitCount[ctr] >= 5){
                int count = 0;
                for (int ctr3 = 0; ctr3 < size(); ctr3++){
                    c = get(ctr3);
                    if (c.getSuitValue() == ctr){
                        remove(c);
                        add(count++, c);
                    }
                    if (count == 5) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Determines if a PokerHand has a full house.
     * @return true if it contains a full house
     */
    public boolean hasFullHouse(){
        Card a,b,c;
        for (int ctr = 0; ctr < size()-2; ctr++){
            a = get(ctr);
            b = get(ctr+1);
            c = get(ctr+2);
            if (a.sameFace(b) && a.sameFace(c)){
                remove(a);
                remove(b);
                remove(c);
                if (hasPair()){
                    add(0, a);
                    add(1, b);
                    add(2, c);
                    return true;
                } else {
                    add(ctr, a);
                    add(ctr+1, b);
                    add(ctr+2, c);
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Determines if a PokerHand has four of a kind.
     * @return true if it contains four of a kind
     */
    public boolean hasFourOfAKind(){
        Card a,b,c,d;
        for (int ctr = 0; ctr < size()-3; ctr++){
            a = get(ctr);
            b = get(ctr+1);
            c = get(ctr+2);
            d = get(ctr+3);
            if (a.sameFace(b) && b.sameFace(c) && c.sameFace(d)){
                remove(a);
                remove(b);
                remove(c);
                remove(d);
                add(0, a);
                add(1, b);
                add(2, c);
                add(3, d);
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
     * @return true if it contains a straight flush
     */
    public boolean hasStraightFlush(){
        int[] suitCount = new int[CardDeck.suits.length];
        Hand nonFlushCards = new Hand();
        for (Card c : this) {
            suitCount[c.getSuitValue()]++;
        }
        /* Reorganizes the cards to reveal the first suit that has 
         * a straight flush. */
        for (int ctr = 0; ctr < CardDeck.suits.length; ctr++){
            if (suitCount[ctr] >= 5){
                for (int ctr2 = 0; ctr2 < size(); ctr2++){
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
        Collections.sort(this);
        Collections.reverse(this);
        return false;
    }
}