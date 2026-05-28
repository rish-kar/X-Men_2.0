/****MODEL****/
theory Bank

begin

builtins: asymmetric-encryption, symmetric-encryption

/* Symmetric session key derived from paired nonces */
functions: k/2

/****RULES****/

rule humansetup:
[ Fr(~ltkH)
, Fr(~ltkB1)
, Fr(~ltkB2)
, Fr(~pw1)
, Fr(~pw2)
, Fr(~nH)
]
--[ OnlyOnce()
, Neq($Human,$B1)
, Neq($Human,$B2)
, Neq($B1,$B2)
, Neq(~pw1,~pw2)
]->
[ !Type($Human,'agentname',$Human)
, !Type($B1,'agentname',$B1)
, !Type($B2,'agentname',$B2)
, !Type($Human,'password',~pw1)
, !Type($Human,'password',~pw2)
, !Type($Human,'nonce',~nH)
, !Ltk($Human,~ltkH)
, !Pk($Human,pk(~ltkH))
, !Ltk($B1,~ltkB1)
, !Pk($B1,pk(~ltkB1))
, !Ltk($B2,~ltkB2)
, !Pk($B2,pk(~ltkB2))
, !Pw($Human,$B1,~pw1)
, !Pw($Human,$B2,~pw2)
, !Accounts($Human,$B1,$B2,~pw1,~pw2,~nH)
, Out(pk(~ltkH))
, Out(pk(~ltkB1))
, Out(pk(~ltkB2))
]

rule Setup:
[ !Accounts($Human,$B1,$B2,pw1,pw2,nH)
, !Ltk($Human,ltkH)
, !Ltk($B1,ltkB1)
, !Ltk($B2,ltkB2)
]
--[ Setup($Human)
, Roles($Human,$B1,$B2)
, Target($Human,$B1)
]->
[ State($Human,'1',<$B1,$B2,nH,pw1,pw2,ltkH>)
, State($B1,'1',<$Human,ltkB1>)
, State($B2,'1',<$Human,ltkB2>)
]

rule Compromise:
[ !Ltk($B,ltkB)
]
--[ RevLtk($B)
]->
[ Out(ltkB)
]

rule H_1:
[ State($Human,'1',<$B1,$B2,nH,pw1,pw2,ltkH>)
, !Pk($B1,pkB1)
]
--[ H()
, Send($Human,'m1',aenc(<$Human,$B1,nH>,pkB1))
, To($B1)
, U_LoginRequest($Human,$B1,nH)
]->
[ State($Human,'2',<$B1,$B2,nH,pw1,pw2,ltkH>)
, Out(aenc(<$Human,$B1,nH>,pkB1))
]

rule H_2_M:
[ State($Human,'2',<$B1,$B2,nH,pw1,pw2,ltkH>)
, In(aenc(<nH,nB>,pk(ltkH)))
]
--[ H()
, Receive($Human,'m2',<nH,nB>)
, Send($Human,'m3',senc(<$Human,pw2>,k(nH,nB)))
, To($B1)
, PasswordAttempt($Human,$B1,pw2,nH,nB)
]->
[ State($Human,'3',<$B1,$B2,nH,nB,pw1,pw2>)
, Out(senc(<$Human,pw2>,k(nH,nB)))
]

rule H_3_Granted:
[ State($Human,'3',<$B1,$B2,nH,nB,pw1,pw2>)
, In('Granted')
]
--[ H()
, Hfin($Human,'Granted')
, LoginSuccess($Human,$B1)
]->
[
]

rule H_3_Denied:
[ State($Human,'3',<$B1,$B2,nH,nB,pw1,pw2>)
, In('Denied')
]
--[ H()
, Hfin($Human,'Denied')
, LoginFailed($Human,$B1)
]->
[
]

rule Bank_1:
[ State($B,'1',<$Human,ltkB>)
, !Pk($Human,pkU)
, Fr(~nB)
, In(aenc(<$Human,$B,nH>,pk(ltkB)))
]
--[ B_Running($B,$Human,nH,~nB)
, Receive($B,'m1',<$Human,$B,nH>)
]->
[ State($B,'2',<$Human,nH,~nB>)
, Out(aenc(<nH,~nB>,pkU))
]

rule Bank_2_OK:
[ State($B,'2',<$Human,nH,nB>)
, !Pw($Human,$B,pw)
, In(senc(<$Human,pw>,k(nH,nB)))
]
--[ LoginOK($B,$Human,pw)
, Receive($B,'m3',<$Human,pw>)
, Commit($B,$Human,'login')
]->
[ Out('Granted')
]

rule Bank_2_Fail:
[ State($B,'2',<$Human,nH,nB>)
, !Pw($Human,$B,storedPw)
, In(senc(<$Human,pw>,k(nH,nB)))
]
--[ LoginFail($B,$Human)
, Neq(pw,storedPw)
]->
[ Out('Denied')
]


/****ENDOFRULES****/

/****RESTRICTIONS****/

restriction OnlyOnce:
	"All #i #j. OnlyOnce()@#i & OnlyOnce()@#j ==> #i = #j"

restriction Inequality:
	"All x #i. Neq(x,x) @ #i ==> F"

restriction notSameRole:
	"All H B1 B2 #i. Roles(H,B1,B2) @ i ==>
		  not H = B1
		& not H = B2
		& not B1 = B2"

/****LEMMAS****/

/* SECURITY GOAL — violated by the forget attack.
   B2 should grant access only when H explicitly addressed B2.
   Tamarin should find a counterexample (the attack trace). */
lemma Auth_B2_requires_H_intent: all-traces
	"All u b2 pw #i.
	   LoginOK(b2,u,pw) @ #i
	   ==> Ex nH #j. U_LoginRequest(u,b2,nH) @ #j & #j < #i"

/* CONDITIONAL SAFETY — should HOLD.
   The password-reuse attack requires B1 to be compromised;
   it cannot succeed against a fully honest ceremony. */
lemma No_Reuse_Attack_Without_Compromise: all-traces
	"All u b1 b2 pw nH nB #i #j.
	   PasswordAttempt(u,b1,pw,nH,nB) @ #i
	 & LoginOK(b2,u,pw) @ #j
	 & not b1 = b2
	   ==> Ex #k. RevLtk(b1) @ #k"

/* ATTACK WITNESS — exists-trace.
   The forget mutation, combined with a compromised B1,
   lets the attacker obtain a session at B2 without H's intent. */
lemma Attack_Forget_Exists: exists-trace
	"Ex u b1 b2 pw nH nB #i #j #k.
	   PasswordAttempt(u,b1,pw,nH,nB) @ #i
	 & LoginOK(b2,u,pw) @ #j
	 & RevLtk(b1) @ #k
	 & not b1 = b2"

/* PASSWORD CONFIDENTIALITY — should HOLD.
   The attacker never learns the password used in the final
   message, because m3 is encrypted with k(nH,nB) and the
   attacker does not know nB (generated fresh by B2). */
lemma Password_Confidentiality: all-traces
	"All u b nH nB pw #i.
	   PasswordAttempt(u,b,pw,nH,nB) @ #i
	   ==> not (Ex #t. K(pw) @ #t)"

/* FUNCTIONAL — a successful login is reachable. */
lemma Functional: exists-trace
	"Ex u b pw #i. LoginOK(b,u,pw) @ #i"

end

/****ENDOFMODEL****/