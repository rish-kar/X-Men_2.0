functions: bal/1

builtins : asymmetric-encryption

rule humansetup:
[ Fr(~gid)
, Fr(~goid)
]
--[ OnlyOnce()
, Neq($oyster,$ccard)
]->
[ !Type($Human,'card',$oyster)
, !Type($Human,'card',$ccard)
, !Type($Human,'balance',bal($oyster))
, !Type($Human,'balance',bal($ccard))
, !Type($GateIn,'gid',~gid)
, !Type($GateIn,'goid',~goid)
, !Wallet($oyster,$ccard)
, !GateID($GateIn,~gid)
, !GateID($GateOut,~goid)
]

rule Setup:
[ !Wallet($oyster,$ccard)
, !GateID($GateIn,~gid)
, !GateID($GateOut,~goid)
]
--[ Setup($Human)
, Roles($Human,$GateIn,$GateOut)
, GateIn($Human,$GateIn)
, GateOut($Human,$GateOut)
]->
[ State($GateIn,'1',<~gid>)
, State($GateOut,'1',<~goid>)
, State($Human,'1',<$oyster,$ccard,bal($oyster),bal($ccard)>)
]

rule H_1:
[ State($Human,'1',<$oyster,$ccard,bal($oyster),bal($ccard)>)
]
--[ H()
, Send($Human,'card',$oyster)
, To($GateIn)
]->
[ State($Human,'2',<$oyster,$ccard,bal($oyster),bal($ccard)>)
, SndS($Human,$GateIn,<'card'>,<$oyster>)
]

rule GateIn_1:
[ State($GateIn,'1',<~gid>)
, RcvS($Human,$GateIn,<'card'>,<$oyster>)
]
--[ Receive($GateIn,$Human,$oyster)
, CommitGid($GateIn,$Human,~gid)
]->
[ State($GateIn,'2',<~gid,$Human,$oyster>)
, SndS($GateIn,$Human,<'card','gid'>,<$oyster,~gid>)
]

rule H_2:
[ State($Human,'2',<$oyster,$ccard,bal($oyster),bal($ccard)>)
, RcvS($GateIn,$Human,<'card','gid'>,<$oyster,~gid>)
]
--[ H()
, Receive($Human,$GateIn,~gid)
, Send($Human,'card',$oyster)
, Send($Human,'balance',bal($oyster))
, Send($Human,'gid',~gid)
, To($GateOut)
]->
[ State($Human,'3',<$oyster,$ccard,bal($oyster),bal($ccard),~gid>)
, SndS($Human,$GateOut,<'card','balance','gid'>,<$oyster,bal($oyster),~gid>)
]

rule GateOut_1:
[ State($GateOut,'1',<~goid>)
, RcvS($Human,$GateOut,<'card','balance','gid'>,<$oyster,bal($oyster),~gid>)
]
--[ Receive($GateOut,$Human,$oyster)
, Receive($GateOut,$Human,bal($oyster))
, Commit($GateOut,$Human,'finish')
]->
[ State($GateOut,'2',<~goid,$oyster,bal($oyster),~gid>)
, SndS($GateOut,$Human,<'card','balance','finish'>,<$oyster,bal($oyster),'finish'>)
]

rule H_3:
[ State($Human,'3',<$oyster,$ccard,bal($oyster),bal($ccard),~gid>)
, RcvS($GateOut,$Human,<'card','balance','finish'>,<$oyster,bal($oyster),'finish'>)
]
--[ H()
, Hfin($Human,'card',$oyster)
]->
[ 
]

rule H_2_M:
[ State($Human,'2',<$oyster,$ccard,bal($oyster),bal($ccard)>)
]
--[ H()
, Receive($Human,$GateIn,~gid)
, Send($Human,'card',$oyster)
, Send($Human,'balance',bal($oyster))
, Send($Human,'gid',~gid)
]->
[ State($Human,'3',<$oyster,$ccard,bal($oyster),bal($ccard)>)
]

rule GateOut_1_M:
[ State($GateOut,'1',<~goid>)
]
-->
[ State($GateOut,'2',<~goid>)
]

rule H_3_M:
[ State($Human,'3',<$oyster,$ccard,bal($oyster),bal($ccard)>)
]
--[ H()
, Hfin($Human,'card',$oyster)
]->
[ 
]

