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

/**
 * Extends Hand with extra blackjack-related methods.
 * @author Yizhe Shen
 */
public class BlackjackHand extends Hand implements Comparable<BlackjackHand>{
    /** Stores the bet on the BlackjackHand. */
    private int bet;

    /**
     * Creates a Blackjack hand with no initial bet.
     * Initializes the bet to 0.
     */
    public BlackjackHand(){
        super();
        bet = 0;
    }
    
    /* Blackjack specific methods */
    /**
     * Sets the bet on this Hand to the specified amount.
     * @param amount the amount to set
     */
    public void setBet(int amount){
        bet = amount;
    }
    
    /**
     * Adds the specified amount to the existing bet.
     * @param amount the amount to add
     */
    public void addBet(int amount){
        bet += amount;
    }
    
    /**
     * Returns the bet on this Hand.
     * @return the bet on this Hand.
     */
    public int getBet(){
        return bet;
    }
    
    /**
     * Clears the bet on this Hand.
     */
    public void clearBet(){
        bet = 0;
    }
    
    /**
     * Returns whether or not this Hand has been hit.
     * @return true if only contains 2 cards
     */
    public boolean hasHit(){
        return getSize() != 2;
    }
    
    /**
     * Determines if a hand is blackjack.
     * If the sum is 21 and there are only 2 cards then it is blackjack.
     * 
     * @return true if the hand is blackjack
     */
    public boolean isBlackjack() {
        return calcSum() == 21 && getSize() == 2;
    }
    
    /**
     * Determines if a hand is bust.
     * If the sum is greater than 21 then it is bust.
     * 
     * @return true if the hand is bust
     */
    public boolean isBust() {
        return calcSum() > 21;
    }
    
    /**
     * Determines if a BlackjackHand is a pair.
     * Useful when determining if splitting is possible. Pairs are determined
     * by their blackjack values.
     * 
     * @return true if the hand is a pair
     */
    public boolean isPair() {
        return getSize() == 2 && get(0).getBlackjackValue() == get(1).getBlackjackValue();
    }
    
    /**
     * Determines if a hand is a soft 17.
     * Used for determining if the dealer needs to hit if the game has been set
     * for the dealer to hit on soft 17.
     * 
     * @return true if the hand is a soft 17
     */
    public boolean isSoft17(){
        if (calcSum() == 17){
            //Recalculate with aces valued at 1 and check if the sum is lower than 17
            int sum=0;
            Card card;
            for (int ctr = 0; ctr < getSize(); ctr++) {
                card = get(ctr);
                if (card.getFace().equals("A")){
                    sum += 1;
                } else {
                    sum += get(ctr).getBlackjackValue();
                }
            }
            return sum < 17;
        }
        return false;
    }
    
    /**
     * Calculates the highest non-busting sum of a BlackjackHand.
     * The highest non-busting sum is returned whenever possible.
     * 
     * @return the sum of the BlackjackHand.
     */
    public int calcSum() {
        int sum = 0, numAces = 0;
        Card card;
        // Add up all the cards and keep track of the number of aces
        for (int ctr = 0; ctr < getSize(); ctr++) {
            card = get(ctr);
            if (card.getFace().equals("A")) {
                numAces++;
            }
            sum += card.getBlackjackValue();
        }
        // Use the lower of each ace while the sum is greater than 21
        for (int ctr = 0; ctr < numAces; ctr++){
            if (sum > 21){
                sum -= 10;
            } else { 
                break;
            }
        }
        return sum;
    }
    
    /**
     * Compares this BlackjackHand to another based on sum.
     * A blackjack is greater than all BlackjackHands except for another
     * blackjack. This method is usually only called when comparing a player's
     * hand to the dealer's.
     * 
     * @param h the BlackjackHand to compare
     * @return -1 if this hand's sum is less or bust, zero for a push, or 1 
     * if this hand's sum is greater
     * @throws NullPointerException if the specified BlackjackHand is null
     */
    @Override
    public int compareTo(BlackjackHand h) {
        if (h == null) {
            throw new NullPointerException();
        }
        int sum = calcSum(), hsum = h.calcSum();
        boolean BJ = isBlackjack();
        boolean hBJ = h.isBlackjack();
        if (sum > 21) {
            return -1;
        } else if (sum == 21) {
            /* Different cases at 21 */
            if (BJ && !hBJ) {
                return 2;
            } else if (BJ && hBJ) {
                return 0;
            } else if (!BJ && hBJ) {
                return -1;
            } else {
                if (hsum == 21) {
                    return 0;
                } else {
                    return 1;
                }
            }
        } else {
            /* Any case other than 21 */
            if (hsum > 21 || hsum < sum) {
                return 1;
            } else if (hsum == sum) {
                return 0;
            } else {
                return -1;
            }
        }
    }
}
