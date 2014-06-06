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

package irccasino.blackjack;

import irccasino.cardgame.CardGame;
import irccasino.cardgame.Record;

/**
 * Stores house statistics for individual shoe sizes.
 * @author Yizhe Shen
 */
class HouseStat extends Record {
    public HouseStat() {
        this(0, 0, 0);
    }

    public HouseStat(int a, int b, int c) {
        super();
        put("decks", a);
        put("rounds", b);
        put("cash", c);
    }

    protected String toFileString() {
        return get("decks") + " " + get("rounds") + " " + get("cash");
    }

    @Override
    public String toString() {
        return CardGame.formatNumber(getInteger("rounds")) + " round(s) have been played using " 
            + CardGame.formatNumber(getInteger("decks")) + " deck shoes. The house has won $"
            + CardGame.formatNumber(getInteger("cash")) + " during those round(s).";
    }
}