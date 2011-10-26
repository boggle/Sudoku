#!/usr/bin/env ruby

require 'rubygems'
require 'neo4j'
require 'utils'

heigth, weight = 0, 0
sudoku = nil

file = File.open(ARGV[0])

data = file.readline.split(',')
height, weight = data[0].to_i, data[1].to_i
sudoku  = []
height.times do |i|
  sudoku.insert i, []
end

begin
  while (line = file.readline)
    data = line.split(',').map { |i| i.to_i }
    sudoku[data[0]].insert data[1], data[2]
  end
rescue EOFError
    file.close
end

Neo4j.start

# Node generation
height.times do |i|
  weight.times do |j|
    cell = add_node({:row => i, :column => j, :color => sudoku[i][j]})
    sudoku[i][j] = cell
  end
end

# Edge generation.
# if they are on the same row
# if they are on the same column
# if they are on the same 3x3 cell

height.times do |row|
  weight.times do |column|
    connect_row_column row, column, sudoku, height, weight
    connect_square row, column, sudoku
  end
end

# Connect with the 3x3 cell

Neo4j.shutdown
