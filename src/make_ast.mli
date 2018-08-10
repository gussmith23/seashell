open Ast

val make_assignment : id -> expression -> command

val make_int : int -> expression

val make_float : float -> expression

val make_bool : bool -> expression

val make_binop : binop -> expression -> expression -> expression

val make_for : id -> expression -> expression -> command -> command

val make_for_impl : id -> expression -> expression -> int -> command -> command

val make_var : id -> expression

val make_if : expression -> command -> command

val make_seq : command -> command -> command

val make_banked_aa : id -> expression -> expression -> expression

val make_aa : id -> expression list -> expression

val make_reassignment : expression -> expression -> command

val make_function : id -> (id * type_node) list -> command -> command

val make_app : id -> expression list -> command

val make_typedef : id -> type_node -> command

val make_muxdef : int -> id -> id -> command
