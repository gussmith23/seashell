open Ast

exception TypeError of string

type context = ((id * int option), type_node) Hashtbl.t
type delta   = (id, type_node) Hashtbl.t

let empty_context = Hashtbl.create 100
let empty_delta   = Hashtbl.create 100

let type_of_id id context = Hashtbl.find context (id, None)

let rec string_of_type = function
  | TBool -> "bool"
  | TInt _ -> "int"
  | TArray (t, _) -> (string_of_type t) ^ " array"
  | TIndex _ -> failwith "Undefined"

let string_of_binop = function
  | BopEq    -> "="
  | BopNeq   -> "!="
  | BopGeq   -> ">="
  | BopLeq   -> "<="
  | BopLt    -> "<"
  | BopGt    -> ">"
  | BopPlus  -> "+"
  | BopMinus -> "-"
  | BopTimes -> "*"
  | BopOr    -> "||"
  | BopAnd   -> "&&"

let bop_type = function
  | BopEq    -> TBool
  | BopNeq   -> TBool
  | BopGeq   -> TBool
  | BopLeq   -> TBool
  | BopLt    -> TBool
  | BopGt    -> TBool
  | BopPlus  -> TInt None
  | BopMinus -> TInt None
  | BopTimes -> TInt None
  | BopAnd   -> TBool
  | BopOr    -> TBool

let is_int = function
  | TInt _ -> true
  | _ -> false

let is_bool = (=) TBool

let rec types_equal t1 t2 =
  match t1, t2 with
  | TInt _, TInt _ -> true
  | TArray (a1, bf1), TArray (a2, bf2) -> bf1=bf2 && types_equal a1 a2
  | TIndex _, TIndex _ -> true
  | t1, t2 -> t1=t2

let legal_op t1 t2 = function
  | BopEq    -> is_int t1 && is_int t2
  | BopNeq   -> is_int t1 && is_int t2
  | BopGeq   -> is_int t1 && is_int t2
  | BopLeq   -> is_int t1 && is_int t2
  | BopLt    -> is_int t1 && is_int t2
  | BopGt    -> is_int t1 && is_int t2
  | BopPlus  -> is_int t1 && is_int t2
  | BopMinus -> is_int t1 && is_int t2
  | BopTimes -> is_int t1 && is_int t2
  | BopAnd   -> is_bool t1 && is_bool t2
  | BopOr    -> is_bool t1 && is_bool t2

let rec check_expr exp (context, delta) =
  match exp with
  | EInt (i, s)                       -> check_int i s (context, delta)
  | EBool _                           -> TBool, (context, delta)
  | EVar x                            -> Hashtbl.find context (x, None), (context, delta)
  | EBinop (binop, e1, e2)            -> check_binop binop e1 e2 (context, delta)
  | EArray _                          -> raise (TypeError "Can't refer to array literal")
  | EArrayExplAccess (id, idx1, idx2) -> check_aa_expl id idx1 idx2 (context, delta)
  | EIndex _                          -> failwith "Implement idx tc"
  | EArrayImplAccess (id, i)          -> check_aa_impl id i (context, delta)

and check_int i is_stat (ctx, dta) = (if is_stat then TInt (Some i) else TInt (None)), (ctx, dta)

and check_binop binop e1 e2 (c, d) =
  check_expr e1 (c, d)  |> fun (t1, c1) ->
  check_expr e2 c1 |> fun (t2, c2) ->
  if legal_op t1 t2 binop then bop_type binop, c2
  else raise (TypeError 
    ("Illegal operation: can't apply operator '" ^
     (string_of_binop binop) ^ 
     "' to " ^ 
     (string_of_type t1) ^ 
     " and " ^ 
     (string_of_type t2)))

and check_array_access_implicit id idx context =
  failwith "Implement implicit loop"

and check_aa_expl id idx1 idx2 c =
  check_expr idx1 c       |> fun (idx1_t, c1) ->
  check_expr idx2 c1      |> fun (idx2_t, c2) ->
  match idx1_t, idx2_t with
  | TInt (None), _ -> raise (TypeError "Bank accessor must be static")
  | TInt (Some i), TInt _ ->
    if Hashtbl.mem c2 (id, Some i) then
      TInt (None), (Hashtbl.remove c2 (id, Some i); c2)
    else raise (TypeError ("Illegal bank access: " ^ (string_of_int i)))
  | _ -> raise (TypeError "Bank accessor must be static") 

and check_aa_impl id i c =
  check_expr i c (* FIXME *)


let rec check_cmd cmd (context, delta) =
  match cmd with
  | CSeq (c1, c2)                 -> check_seq c1 c2 (context, delta)
  | CIf (cond, cmd)               -> check_if cond cmd (context, delta)
  | CFor (x, r1, r2, body)        -> check_for x r1 r2 body (context, delta)
  | CForImpl (x, r1, r2, u, body) -> check_for_impl x r1 r2 body (context, delta)
  | CAssign (x, e1)               -> check_assignment x e1 (context, delta)
  | CReassign (target, exp)       -> check_reassign target exp (context, delta)
  | CFuncDef (id, args, body)     -> check_funcdef id args body (context, delta)
  | CApp (id, args)               -> check_app id args (context, delta)

and check_seq c1 c2 (context, delta) =
  check_cmd c1 (context, delta)
  |> fun (context', delta') -> check_cmd c2 (context', delta')

and check_if cond cmd (context, delta) =
  match check_expr cond context with
  | TBool, c -> check_cmd cmd c
  | _ -> raise (TypeError "Non-boolean conditional")
  
and check_for id r1 r2 body (c, delta) =
  check_expr r1 c       |> fun (r1_type, c1) ->
  check_expr r2 c1      |> fun (r2_type, c2) ->
  match r1_type, r2_type with
  | TInt _, TInt _ -> 
    Hashtbl.add c2 (id, None) (TInt None); check_cmd body c2
  | _ -> raise (TypeError "Range start/end must be integers")

and check_for_impl id idx1 idx2 body (context, delta) =
  check_for id idx1 idx2 body context (* FIXME *)

and add_array_banks bf id bank_num (context, delta) t i =
  if i=bank_num then context
  else 
    (Hashtbl.add context (id, Some i) (TArray (t, bf)); 
     (add_array_banks bf id bank_num context t (i+1)))

and check_assignment id exp (context, delta) =
  match exp with
  | EArray (t, b, _) -> 
    Hashtbl.add context (id, None) (TArray (t, b)); 
    add_array_banks b id b context t 0
  | other_exp -> 
    check_expr other_exp context |> fun (t, c) ->
    Hashtbl.add c (id, None) t; c

and check_reassign target exp (context, delta) =
  match target, exp with
  | EArrayExplAccess (id, idx1, idx2), expr ->
    check_aa_expl id idx1 idx2 context |> fun (t_arr, c) ->
    check_expr exp c                   |> fun (t_exp, c') ->
    if types_equal t_arr t_exp then c'
    else raise (TypeError "Tried to populate array with incorrect type")
  | EVar id, expr -> context
  | EArrayImplAccess (id, idx), expr -> context (* FIXME *)
  | _ -> raise (TypeError "Used reassign operator on illegal types")

and check_funcdef id args body (context, delta) =
  (* FIXME: this is a little wonky *)
  List.iter (fun (e, t) -> Hashtbl.add context (e, None) t) args;
  let context' = check_cmd body context in
  List.iter (fun (e, t) -> Hashtbl.remove context' (e, None)) args;
  Hashtbl.add context' (id, None) (TFunc (List.map (fun (_, t) -> t) args));
  context'

and check_app id args (context, delta) =
  let argtypes = List.map (fun a -> check_expr a context |> fst) args in
  match Hashtbl.find context (id, None) with
  | TFunc param_types -> 
    if List.exists2 
      (fun arg param -> not (types_equal arg param)) argtypes param_types
    then raise (TypeError ("Illegal arg type supplied to " ^ id))
    else context
  | _ -> raise (TypeError (id ^ " is not a function and cannot be applied"))

