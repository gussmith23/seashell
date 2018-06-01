%{
open Ast
open Make_ast
%}

%token EOF

%token <int> INT
%token <string> ID

%token LET
%token EQUAL
%token SEMICOLON

%token FOR

%token LPAREN
%token RPAREN
%token LBRACK
%token RBRACK
%token LSQUARE
%token RSQUARE

%token NEQ
%token GEQ
%token LEQ
%token LT
%token GT
%token EQ

%token PLUS
%token MINUS
%token TIMES

%token TRUE
%token FALSE

%token INT_ANNOTATION
%token BOOL_ANNOTATION

%token RANGE_DOTS

%token UNROLL

%token REASSIGN

%token IF

%left EQUAL

%start <Ast.command list> prog

%%

prog:
  | e = separated_list(SEMICOLON, cmd); EOF
    { e } ;

cmd:
  | IF; LPAREN; b = expr; RPAREN; LBRACK; body = separated_list(SEMICOLON, cmd); RBRACK
    { make_if b body }
  | id = ID; LSQUARE; index = expr; RSQUARE; REASSIGN; e = expr
    { make_array_update id index e }
  | t = type_annotation; x = ID; LSQUARE; s = INT; RSQUARE
    { make_assignment x (make_array s t) }
  | FOR; LPAREN; LET; x = ID; EQUAL; x1 = expr; RANGE_DOTS; x2 = expr; RPAREN; 
    LBRACK; e = separated_list(SEMICOLON, cmd); RBRACK
    { make_for x x1 x2 e }
  | LET; x = ID; EQUAL; e1 = expr
    { make_assignment x e1 } ;

expr:
  | x = INT
    { make_int x }
  | TRUE
    { make_bool true }
  | FALSE
    { make_bool false }
  | x = ID
    { make_var x }
  | e1 = expr; bop = binop; e2 = expr
    { make_binop bop e1 e2 } ;

type_annotation:
  | BOOL_ANNOTATION { ABool }
  | INT_ANNOTATION { AInt }

%inline binop:
  | NEQ { BopNeq }
  | GEQ { BopGeq }
  | LEQ { BopLeq }
  | LT  { BopLt }
  | GT  { BopGt }
  | EQ  { BopEq }
  | PLUS { BopPlus }
  | MINUS { BopMinus }
  | TIMES { BopTimes } ;
