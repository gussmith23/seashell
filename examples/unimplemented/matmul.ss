func mmul(a: float[32][32 bank(32)], b: float[32 bank(32)][32], c: float[32][32]) {

  for (let i = 0..31) unroll 1 {
    for (let j = 0..31) unroll 1 {
      for (let k = 0..31) unroll 32 {
        c{i}[j] := a{i}[k] * b{k}[j];
      } 
    } 
  } 

}