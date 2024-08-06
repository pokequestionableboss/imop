pointcut pc1 before MethodCall(* *.*ListIterator.previous())
pointcut pc2 before MethodCall(* *.*Iterator.next())
pointcut pc3 before MethodCall(* *.*Iterator.remove())

event e1("ListIterator_RemoveOnce", name, [getMethodReceiver, getMethodResult]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("ListIterator_RemoveOnce", name, [getMethodReceiver, getMethodResult]) on pc2 to Monitor.receiveEvents(String, String, List)
