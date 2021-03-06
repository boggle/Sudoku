use std;

import std::io;
import std::str;
import std::uint;
import std::u8;
import std::vec;
import std::bitv;

// Computes a single solution to a given 9x9 sudoku
//
// Input is read from stdin; expected line-based format is:
// 
// 9,9
// <row>,<column>,<color>
// ...
//
// Row and column are 0-based (i.e. <= 8) and color is 1-based (>=1,<=9).
// A color of 0 indicates an empty field.
//
// (Example sudoku is included in comment at end of file)
//
// Author: Stefan Plantikow <stefan.plantikow@googlemail.com>
//
//
// Main purpose was to take rust for a spin:
//
// - This is really interesting work; but these are the issues I had:
// - Lack of "ret" from enclosing function in nested iterators
// - Lack of loop labels (Complexifies logic, is this due to the 
// typestate stuff, i.e. to keep DF analysis tractable?)
// - "For each" for iterators seems not to have been implemented yet
// - No automatic lambda enclosure ("bind"). Wonder why?
// - Would be nice to typecast on bind (i.e. treat uint -> t as u8 -> t)
// - Multidim array init is way to verbose
// - Is there something like go interfaces? or do I have to use objects?
// - I miss classic for. Why drop what people already know and are used to?
// - Immutable as default but no tail recursion? Why?
// - How do I write a default "toString" for a data type? Not clear.
// 
// This code is licensed under the BSD license. No warranty for anything.
//

export grid_t, read_grid, solve_grid, write_grid;

// Internal type  of sudoku grids
type grid = [[mutable u8]];

// Exported type of sudoku grids
tag grid_t { grid_ctor(grid); }

// Read a sudoku problem from file f
fn read_grid(f: io::reader) -> grid_t {
    assert f.read_line() == "9,9"; /* Assert first line is exactly "9,9" */

    let g = vec::init_fn({|_i| ret vec::init_elt_mut(0 as u8, 10u);}, 10u);
    while !f.eof() { // FIXME: Replace with iterator
        // FIXME: There really should be a more unicode compliant call
        let comps = str::split(str::trim(f.read_line()), ',' as u8);
        if vec::len(comps) >= 3u {
            let row     = uint::from_str(comps[0]) as u8;
            let col     = uint::from_str(comps[1]) as u8;
            g[row][col] = uint::from_str(comps[2]) as u8;
        }
    }
    ret grid_ctor(g);
}

// Solve sudoku grid
fn solve_grid(g: grid_t) {
    fn next_color(g: grid, row: u8, col: u8, start_color: u8) -> bool {
        if start_color < 10u8 {
            // Colors not yet used
            let avail = bitv::create(10u, false);       
            u8::range(start_color, 10u8) { |color|
                bitv::set(avail, color as uint, true);
            }

            // Drop colors already in use in neighbourhood
            drop_colors(g, avail, row, col);

            // Find first remaining color that is available
            let i = 1 as uint;
            while i < (10 as uint) {
                if bitv::get(avail, i) {
                    g[row][col] = i as u8;
                    ret true;
                }
                i += 1 as uint; /* else */
            }
        }
        g[row][col] = 0u8;
        ret false;
    }

    // Find colors available in neighbourhood of (row, col)
    fn drop_colors(g: grid, avail: bitv::t, row: u8, col: u8) {
        fn drop_color(g: grid, colors: bitv::t, row: u8, col: u8) {
            let color = g[row][col];
            if color != 0u8 { bitv::set(colors, color as uint, false); }
        }

        let it = bind drop_color(g, avail, _, _);

        u8::range(0u8, 9u8) { |idx| 
            it(idx, col); /* Check same column fields */
            it(row, idx); /* Check same row fields */
        }

        // Check same block fields
        let row0 = (row / 3u8) * 3u8;
        let col0 = (col / 3u8) * 3u8;
        u8::range(row0, row0 + 3u8) { |alt_row|
            u8::range(col0, col0 + 3u8) { |alt_col| it(alt_row, alt_col); }
        }
    }

    let work: [(u8, u8)] = []; /* Queue of uncolored fields */
    u8::range(0u8, 9u8) { |row|
        u8::range(0u8, 9u8) { |col|
            let color = (*g)[row][col];
            if color == 0u8 { work += [(row, col)]; } 
        }
    }
    
    let ptr = 0u;
    let end = vec::len(work);
    while (ptr < end) {
        let (row, col) = work[ptr];
        // Is there another color to try?
        if next_color(*g, row, col, (*g)[row][col] + (1 as u8)) { 
            //  Yes: Advance work list
            ptr = ptr + 1u;
        } else { 
            // No: redo this field aft recoloring pred; unless there is none
            if ptr == 0u { fail "No solution found for this sudoku"; } 
            ptr = ptr - 1u;
        }
    }
}

fn write_grid(f: io::writer, g: grid_t) {
    u8::range(0u8, 9u8) { |row|
        f.write_str(#fmt("%u", (*g)[row][0] as uint));
        u8::range(1u8, 9u8) { |col| 
            f.write_str(#fmt(" %u", (*g)[row][col] as uint));
        }
        f.write_char('\n');
     }
}

fn main() {
    let grid = read_grid(io::stdin());
    solve_grid(grid);
    write_grid(io::stdout(), grid);
}

/* Example sudoku
9,9
0,1,4
0,3,6
0,7,3
0,8,2
1,2,8
1,4,2
2,0,7
2,3,8
3,3,5
4,1,5
4,5,3
4,6,6
5,0,6
5,1,8
5,7,9
6,1,9
6,2,5
6,5,6
6,7,7
7,4,4
7,7,6
8,0,4
8,5,7
8,6,2
8,8,3
*/