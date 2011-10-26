class Cell
  include Neo4j::NodeMixin
  index :row
  index :column
  index :color
end

def add_node(props)
  Neo4j::Transaction.run do
    Cell.new(props)
  end
end

def add_relationship(from, to)
  Neo4j::Transaction.run do 
    Neo4j::Relationship.new(:rel, from, to)
  end
end

def connect_row_column(row, column, sudoku, rows, columns)
  source = sudoku[row][column]
  s_row, s_column = row, 0
  columns.times do |target_column|
    next if target_column == column
    target = sudoku[row][target_column]
    add_relationship source, target
  end
  rows.times do |target_row|
    next if target_row == row
    target = sudoku[target_row][column]
    add_relationship source, target
  end
end

def connect_square(row, column, sudoku)
  source = sudoku[row][column]
  init_row, init_column = 3 * (row/3), 3 * (column/3)
  3.times do |row_offset|
    3.times do |column_offset|
      target_row, target_column = init_row+row_offset, init_column+column_offset
      target = sudoku[target_row][target_column]
      add_relationship source, target unless (target_row == row and target_column == column)
    end
  end
end

