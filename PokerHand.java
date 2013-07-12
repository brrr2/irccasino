package irccasino;

import org.pircbotx.Colors;

public class PokerHand extends Hand implements Comparable<PokerHand>{
	private int value;
	public PokerHand(){
		super();
		value = -1;
	}
	
	public int compareTo(PokerHand h){
		int value = this.getValue();
		int otherValue = h.getValue();
		
		if (value == otherValue){
			switch (value) {
				case 8: return this.get(0).getFaceValue() - h.get(0).getFaceValue();
				case 7: return this.get(0).getFaceValue() - h.get(0).getFaceValue();
				case 6:	
					if (this.get(0).getFaceValue() - h.get(0).getFaceValue() == 0){
						return this.get(3).getFaceValue() - h.get(3).getFaceValue();
					} else {
						return this.get(0).getFaceValue() - h.get(0).getFaceValue();
					}
				case 5: 
					for (int ctr = 0; ctr < 5; ctr++){
						if (this.get(ctr).getFaceValue() != h.get(ctr).getFaceValue()){
							return this.get(ctr).getFaceValue() - h.get(ctr).getFaceValue();
						}
					}
					return 0;
				case 4:
					return this.get(0).getFaceValue() - h.get(0).getFaceValue();
				case 3:
					if (this.get(0).getFaceValue() - h.get(0).getFaceValue() == 0){
						for (int ctr = 3; ctr < 5; ctr++){
							if (this.get(ctr).getFaceValue() != h.get(ctr).getFaceValue()){
								return this.get(ctr).getFaceValue() - h.get(ctr).getFaceValue();
							}
						}
					} else {
						return this.get(0).getFaceValue() - h.get(0).getFaceValue();
					}
					return 0;
				case 2: 
					if (this.get(0).getFaceValue() != h.get(0).getFaceValue()){
						return this.get(0).getFaceValue() - h.get(0).getFaceValue();
					} else if (this.get(2).getFaceValue() != h.get(2).getFaceValue()){
						return this.get(2).getFaceValue() - h.get(2).getFaceValue();
					} else {
						return this.get(4).getFaceValue() - h.get(4).getFaceValue();
					}
				case 1:
					if (this.get(0).getFaceValue() != h.get(0).getFaceValue()){
						return this.get(0).getFaceValue() - h.get(0).getFaceValue();
					} else {
						for (int ctr = 2; ctr < 5; ctr++){
							if (this.get(ctr).getFaceValue() != h.get(ctr).getFaceValue()){
								return this.get(ctr).getFaceValue() - h.get(ctr).getFaceValue();
							}
						}
						return 0;
					}
				default:
					for (int ctr = 0; ctr < 5; ctr++){
						if (this.get(ctr).getFaceValue() != h.get(ctr).getFaceValue()){
							return this.get(ctr).getFaceValue() - h.get(ctr).getFaceValue();
						}
					}
					return 0;
			}
		} else {
			return value - otherValue;
		}
	}
	
	public int getValue(){
		if (value == -1){
			value = calcValue();
		}
		return value;
	}
	public void resetValue(){
		value = -1;
	}
	
	public String getName(){
		switch (this.getValue()) {
			case 8: return "Straight Flush";
			case 7: return "Four of a Kind";
			case 6: return "Full House";
			case 5: return "Flush";
			case 4: return "Straight";
			case 3: return "Three of a Kind";
			case 2: return "Two Pairs";
			case 1: return "Pair";
			default: return get(0).toString()+Colors.NORMAL+" High";
		}
	}
	
	private int calcValue(){
		// Always check the hands in order of descending value
		if (hasStraightFlush(this)){	// Straight flush = 8
			return 8;
		} else if (hasFourOfAKind(this)){	// Four of a kind = 7
			return 7;
		} else if (hasFullHouse(this)){	// Full house = 6
			return 6;
		} else if (hasFlush(this)){	// Flush = 5
			return 5;
		} else if (hasStraight(this)){	// Straight = 4
			return 4;
		} else if (hasThreeOfAKind(this)){	// Three of a kind = 3
			return 3;
		} else if (hasTwoPair(this)){	// Two-pair = 2
			return 2;
		} else if (hasPair(this)){	// Pair = 1
			return 1;
		} else {	// High card = 0
			return 0;
		}
	}
	
	public String toString(){
		return this.subHand(0,5).toString();
	}
	
	/*
	 * A smattering of methods to check for various poker card combinations.
	 * Methods require that hands be sorted in descending order.
	 */
	public static boolean hasPair(Hand h){
		Card a,b;
		for (int ctr = 0; ctr < h.getSize()-1; ctr++){
			a = h.get(ctr);
			b = h.get(ctr+1);
			if (a.getFace() == b.getFace()){
				h.remove(a);
				h.remove(b);
				h.add(a, 0);
				h.add(b, 1);
				return true;
			}
		}
		return false;
	}
	public static boolean hasTwoPair(Hand h){
		Card a,b;
		for (int ctr = 0; ctr < h.getSize()-3; ctr++){
			a = h.get(ctr);
			b = h.get(ctr+1);
			if (a.getFace() == b.getFace()){
				h.remove(a);
				h.remove(b);
				if (hasPair(h)){
					h.add(a, 0);
					h.add(b, 1);
					return true;
				} else {
					h.add(a, ctr);
					h.add(b, ctr+1);
					return false;
				}
			}
		}
		return false;
	}
	public static boolean hasThreeOfAKind(Hand h){
		Card a,b,c;
		for (int ctr = 0; ctr < h.getSize()-2; ctr++){
			a = h.get(ctr);
			b = h.get(ctr+1);
			c = h.get(ctr+2);
			if (a.getFace() == b.getFace() && b.getFace() == c.getFace()){
				h.remove(a);
				h.remove(b);
				h.remove(c);
				h.add(a, 0);
				h.add(b, 1);
				h.add(b, 2);
				return true;
			}
		}
		return false;
	}
	public static boolean hasStraight(Hand h){
		Card c;
		boolean[] cardValues = new boolean[CardDeck.faces.length+1];
		for (int ctr = 0; ctr < h.getSize(); ctr++){
			c = h.get(ctr);
			if (c.getFace().equals("A")){
				cardValues[0] = true;
			}
			cardValues[c.getFaceValue()+1] = true;
		}
		for (int ctr = 0; ctr < cardValues.length-4; ctr++){
			if (cardValues[ctr] && cardValues[ctr+1] && cardValues[ctr+2] && 
				cardValues[ctr+3] && cardValues[ctr+4]){
				for (int ctr2=0; ctr2<5; ctr2++){
					for (int ctr3=0; ctr3<h.getSize(); ctr3++){
						c = h.get(ctr3);
						if ((ctr == 0 && c.getFace().equals("A")) || c.getFaceValue()+1 == ctr){
							h.remove(c);
							h.add(c,ctr2);
							break;
						}
					}
				}
				return true;
			}
		}
		return false;
	}
	public static boolean hasFlush(Hand h){
		int[] suitCount = new int[CardDeck.suits.length];
		Card c;
		for (int ctr = 0; ctr < h.getSize(); ctr++){
			c = h.get(ctr);
			suitCount[c.getSuitValue()]++;
		}
		for (int ctr = 0; ctr < suitCount.length; ctr++){
			if (suitCount[ctr] >= 5){
				int count = 0;
				for (int ctr3=0; ctr3<h.getSize(); ctr3++){
					c = h.get(ctr3);
					if (c.getSuitValue() == ctr){
						h.remove(c);
						h.add(c,count++);
					}
				}
				return true;
			}
		}
		return false;
	}
	public static boolean hasFullHouse(Hand h){
		Card a,b,c;
		for (int ctr = 0; ctr < h.getSize()-2; ctr++){
			a = h.get(ctr);
			b = h.get(ctr+1);
			c = h.get(ctr+2);
			if (a.getFace() == b.getFace() && a.getFace() == c.getFace()){
				h.remove(a);
				h.remove(b);
				h.remove(c);
				boolean hp = hasPair(h);
				if (hp){
					h.add(a,0);
					h.add(b,1);
					h.add(c,2);
					return true;
				} else {
					h.add(a,ctr);
					h.add(b,ctr+1);
					h.add(c,ctr+2);
					return false;
				}
			}
		}
		return false;
	}
	public static boolean hasFourOfAKind(Hand h){
		Card a,b,c,d;
		for (int ctr = 0; ctr < h.getSize()-3; ctr++){
			a = h.get(ctr);
			b = h.get(ctr+1);
			c = h.get(ctr+2);
			d = h.get(ctr+3);
			if (a.getFace() == b.getFace() && b.getFace() == c.getFace() 
					&& c.getFace() == d.getFace()){
				h.remove(a);
				h.remove(b);
				h.remove(c);
				h.remove(d);
				h.add(a,0);
				h.add(b,1);
				h.add(c,2);
				h.add(d,2);
				return true;
			}
		}
		return false;
	}
	public static boolean hasStraightFlush(Hand h){
		if (hasFlush(h)){
			Hand nonFlushCards = new Hand();
			for(int ctr = 0; ctr<h.getSize(); ctr++){
				if (h.get(ctr).getSuitValue() != h.get(0).getSuitValue()){
					nonFlushCards.add(h.get(ctr));
					h.remove(ctr);
				}
			}
			if (hasStraight(h)){
				h.addAll(nonFlushCards);
				return true;
			} else {
				h.addAll(nonFlushCards);
				return false;
			}
		}
		return false;
	}
}
