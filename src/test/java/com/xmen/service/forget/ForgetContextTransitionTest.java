package com.xmen.service.forget;

import com.xmen.model.Atom;
import com.xmen.model.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ForgetContext transition behavior")
class ForgetContextTransitionTest {

  @Test
  @DisplayName("m == m1: knowledge does not gain m1 and Forget removes m1")
  void updateForTransitionSameMessage() {
    Message m1 = new Atom("m1");
    Set<Message> knowledge = new LinkedHashSet<>();
    Set<Message> forgetSet = new LinkedHashSet<>();
    forgetSet.add(m1);

    ForgetContext ctx = new ForgetContext(knowledge, forgetSet, ForgetContext.BlockingMode.CASE1_WEAK, null);
    ctx.updateForTransition(m1, m1);

    assertFalse(ctx.getKnowledge().contains(m1));
    assertFalse(ctx.getForgetSet().contains(m1));
  }

  @Test
  @DisplayName("m != m1: knowledge gains m1 and Forget gains m")
  void updateForTransitionDifferentMessage() {
    Message m1 = new Atom("m1");
    Message m2 = new Atom("m2");
    Set<Message> knowledge = new LinkedHashSet<>();
    Set<Message> forgetSet = new LinkedHashSet<>();

    ForgetContext ctx = new ForgetContext(knowledge, forgetSet, ForgetContext.BlockingMode.CASE1_WEAK, null);
    ctx.updateForTransition(m1, m2);

    assertTrue(ctx.getKnowledge().contains(m1));
    assertTrue(ctx.getForgetSet().contains(m2));
    assertFalse(ctx.getForgetSet().contains(m1));
  }

  @Test
  @DisplayName("Unforget-on-receive removes m from Forget and adds to knowledge")
  void updateForTransitionUnforgetOnReceive() {
    Message m2 = new Atom("m2");
    Set<Message> knowledge = new LinkedHashSet<>();
    Set<Message> forgetSet = new LinkedHashSet<>();
    forgetSet.add(m2);

    ForgetContext ctx = new ForgetContext(knowledge, forgetSet, ForgetContext.BlockingMode.CASE1_WEAK, null);
    ctx.updateForTransition(m2, null);

    assertTrue(ctx.getKnowledge().contains(m2));
    assertTrue(ctx.getForgetSet().isEmpty());
  }

  @Test
  @DisplayName("Idempotence: repeated update yields same state")
  void updateForTransitionIdempotent() {
    Message m1 = new Atom("m1");
    Message m2 = new Atom("m2");

    ForgetContext ctxOnce = new ForgetContext(new LinkedHashSet<>(), new LinkedHashSet<>(),
        ForgetContext.BlockingMode.CASE1_WEAK, null);
    ctxOnce.updateForTransition(m1, m2);

    ForgetContext ctxTwice = new ForgetContext(new LinkedHashSet<>(), new LinkedHashSet<>(),
        ForgetContext.BlockingMode.CASE1_WEAK, null);
    ctxTwice.updateForTransition(m1, m2);
    ctxTwice.updateForTransition(m1, m2);

    assertEquals(ctxOnce.getKnowledge(), ctxTwice.getKnowledge());
    assertEquals(ctxOnce.getForgetSet(), ctxTwice.getForgetSet());
  }

  @Test
  @DisplayName("Both null: no-op")
  void updateForTransitionBothNullNoOp() {
    Message m1 = new Atom("m1");
    Set<Message> knowledge = new LinkedHashSet<>();
    Set<Message> forgetSet = new LinkedHashSet<>();
    knowledge.add(m1);
    forgetSet.add(new Atom("m2"));

    ForgetContext ctx = new ForgetContext(knowledge, forgetSet, ForgetContext.BlockingMode.CASE1_WEAK, null);
    ctx.updateForTransition(null, null);

    assertTrue(ctx.getKnowledge().contains(m1));
    assertEquals(1, ctx.getForgetSet().size());
  }
}

