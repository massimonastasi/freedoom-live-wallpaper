# Third-party notices

This application is licensed under the **GNU General Public License, version 2**, whose
full text is in [LICENSE](LICENSE). It is GPL-2.0 by obligation, not by preference: it
contains constants and tables taken from the id Software engine source release, which is
GPL-2.0, and a work containing them must be distributed under the same terms.

---

## id Software — engine source code (`linuxdoom-1.10`)

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
| Creature speed, health, radius, pain chance, reaction time | `info.c`, `mobjinfo[]` |
| Animation frame counts and durations | `info.c`, `states[]` |
| Missile speeds and damage | `info.c` |
| Armour absorption, ammunition amounts, skill-level effects | `p_inter.c`, `g_game.c`, `p_mobj.c` |
| Damage formulas for shots and melee | `p_pspr.c`, `p_enemy.c` |

**Every one of these carries a comment naming the file and symbol it came from.** Those
comments are not decoration: they are the attribution this licence requires and the
record of what is derived and what is not. They must not be removed.

The behaviours reproduced from that source — the chase-direction search, the amortised
target lookup, the stochastic firing range — are described in comments citing the same
files, and were written from those descriptions rather than copied.

---

## The Freedoom project — game assets

The application bundles a **reduced subset** of `freedoom2.wad`: 639 of its 3610 lumps,
being the sprites, palette, floor texture and numerals actually drawn. Nothing is added
and nothing is altered; lumps that are never used are omitted. The subset retains
Freedoom's own `FREEDOOM` identifying lump.

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

The third condition covers **endorsement and promotion**, not attribution. Crediting
Freedoom is required; naming the published application after it requires written
permission, which is a publication-time decision and does not affect this repository.

---

## Trademarks

the engine is a trademark of id Software LLC / ZeniMax Media Inc. This project is not
affiliated with, endorsed by or sponsored by either, nor by the Freedoom project.

No commercial game data is distributed here in any form. A user may point the application
at an IWAD they already own, which is read from their own device and never copied,
uploaded or redistributed.
