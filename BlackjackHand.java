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

public class BlackjackHand extends Hand implements Comparable<BlackjackHand>{
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
	public void setBet(int amount){
    	bet = amount;
    }
    public void addBet(int amount){
        bet += amount;
    }
    public int getBet(){
        return bet;
    }
    public void clearBet(){
        bet = 0;
    }
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
    public boolean isBusted() {
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
    
    @Override
    public int compareTo(BlackjackHand h) {
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
