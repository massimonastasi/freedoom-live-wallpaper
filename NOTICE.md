# Third-party notices

This application is licensed under the **GNU General Public License, version 2**, whose
full text is in [LICENSE](LICENSE). It is GPL-2.0 by obligation, not by preference: it
contains constants and tables taken from the `linuxdoom-1.10` source release by id Software,
which is GPL-2.0, and a work containing them must be distributed under the same terms.

---

## id Software — `linuxdoom-1.10` source release

Source: <https://github.com/id-Software/DOOM>, released under GPL-2.0. The source files
carry the id Software copyright quoted below; the rights are now held by **ZeniMax Media
Inc.**, which is how that repository states its own copyright.

**Non-profit use.** That repository's README says the source "is released for your non-profit
use." GPL-2.0 itself permits charging a fee, so the two do not say the same thing, and this
project resolves the difference by taking the narrower reading: **this application is free,
carries no advertising, no paid version and no purchases of any kind, and is not to be
monetised in any form.** That is a condition of using this material, not a preference, and it
binds anyone who takes this work further under the same terms.

> Copyright (C) 1993-1996 id Software, Inc.
>
> This program is free software; you can redistribute it and/or modify it under the terms
> of the GNU General Public License as published by the Free Software Foundation; either
> version 2 of the License, or (at your option) any later version.
>
> This program is distributed in the hope that it will be useful, but WITHOUT ANY
> WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
> PARTICULAR PURPOSE. See the GNU General Public License for more details.

**What is taken.** No engine code is used: there is no renderer, no BSP traversal, no
level loading and no play simulation from that source. What is reproduced are gameplay
*values and small tables*, so that everything in the scene moves and animates at the rate
the original does:

| Taken | Origin |
|---|---|
| `rndtable[]`, the 256-byte pseudo-random table | `m_random.c` |
| `xspeed[]` / `yspeed[]` direction tables, `opposite[]` | `p_enemy.c` |
| Creature speed, radius and pain chance | `info.c`, `mobjinfo[]` |
| Animation frame counts and durations | `info.c`, `states[]` |
| Missile speeds | `info.c` |
| Armour absorption, ammunition amounts and limits | `p_inter.c` |
| The skill names, and `G_PlayerReborn` handing back the pistol | `g_game.c` |
| Fixed-point movement, friction and stop speed | `p_mobj.c` |

**What is not taken.** Health and damage are **ours**, not that source's: the figures in
[docs/BALANCE.md](docs/BALANCE.md) were chosen for a wallpaper and replace `mobjinfo`'s health,
the missile damage, and the random damage formulas of `p_pspr.c` and `p_enemy.c`, which are no
longer reproduced in any form. The skill levels are names only; none of the difficulty scaling
`g_game.c` applies is here.

**Every one of these carries a comment naming the file and symbol it came from.** Those
comments are not decoration: they are the attribution this licence requires and the
record of what is derived and what is not. They must not be removed.

The behaviours reproduced from that source — the chase-direction search, the amortised
target lookup, the stochastic firing range — are described in comments citing the same
files, and were written from those descriptions rather than copied.

---

## The Freedoom project — game assets

Source: <https://github.com/freedoom/freedoom>. Licence text below reproduced from their
`COPYING.adoc`. The individual contributors are listed in that repository's `CREDITS`,
`CREDITS-LEVELS` and `CREDITS-MUSIC` files, which are the authoritative record of who made
what and are not restated here.

The application bundles a **reduced subset** of `freedoom2.wad` from **Freedoom Phase 2,
version 0.13.0** — the version is read from the WAD's own `FREEDOOM` lump, not assumed. 639
of its 3610 lumps: the sprites, palette, floor flats and numerals actually drawn. Nothing is
added and nothing is altered; lumps that are never used are omitted. The subset retains
Freedoom's own `FREEDOOM` identifying lump, so what ships stays recognisable as theirs.

> Copyright © 2001-2024
> Contributors to the Freedoom project.  All rights reserved.
>
> Redistribution and use in source and binary forms, with or without modification, are
> permitted provided that the following conditions are met:
>
>   * Redistributions of source code must retain the above copyright notice, this list of
>     conditions and the following disclaimer.
>   * Redistributions in binary form must reproduce the above copyright notice, this list
>     of conditions and the following disclaimer in the documentation and/or other
>     materials provided with the distribution.
>   * Neither the name of the Freedoom project nor the names of its contributors may be
>     used to endorse or promote products derived from this software without specific
>     prior written permission.
>
> THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
> EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
> MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
> THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
> SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
> PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
> INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
> LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
> OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The third condition covers **endorsement and promotion**, not attribution. Crediting Freedoom
is required, and is done throughout; naming a derived product after it is what the condition
forbids without written permission. This application is called **Prof Live Wallpaper** and its
repository `prof-live-wallpaper`: the Freedoom name appears here only to say whose artwork this
is, which is attribution and is required, never in the product's own name.

---

## Affiliation

This project is independent. It is not affiliated with, endorsed by or sponsored by the
Freedoom project, by id Software LLC, or by any other rights holder. Trademarks belonging
to others are the property of their respective owners.

No commercial game data is distributed here in any form. A user may point the application
at an IWAD they already own, which is read from their own device and never copied,
uploaded or redistributed.
