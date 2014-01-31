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

import irccasino.Stats;
import irccasino.cardgame.CardGame;

/**
 * Stores house statistics for individual shoe sizes.
 * @author Yizhe Shen
 */
class HouseStat extends Stats {
    public HouseStat() {
        this(0, 0, 0);
    }

    public HouseStat(int a, int b, int c) {
        super();
        set("decks", a);
        set("rounds", b);
        set("cash", c);
    }

    protected String toFileString() {
        return get("decks") + " " + get("rounds") + " " + get("cash");
    }

    @Override
    public String toString() {
        return CardGame.formatNumber(get("rounds")) + " round(s) have been played using " 
            + CardGame.formatNumber(get("decks")) + " deck shoes. The house has won $"
            + CardGame.formatNumber(get("cash")) + " during those round(s).";
    }
}