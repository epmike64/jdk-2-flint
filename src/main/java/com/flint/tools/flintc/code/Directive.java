/*
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.flint.tools.flintc.code;

import com.flint.tools.flintc.code.Symbol.ClassSymbol;
import com.flint.tools.flintc.code.Symbol.ModuleSymbol;
import com.flint.tools.flintc.code.Symbol.PackageSymbol;
import com.flint.tools.flintc.util.DefinedBy;
import com.flint.tools.flintc.util.DefinedBy.Api;
import com.flint.tools.flintc.util.JCList;

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.ModuleElement.DirectiveVisitor;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;


/**
 *  Root class for the directives that may appear in module compilation units.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public abstract class Directive implements ModuleElement.Directive {

    /** Flags for RequiresDirective. */
    public enum RequiresFlag {
        TRANSITIVE(0x0020),
        STATIC_PHASE(0x0040),
        SYNTHETIC(0x1000),
        MANDATED(0x8000),
        EXTRA(0x10000);

        // overkill? move to ClassWriter?
        public static int value(Set<RequiresFlag> s) {
            int v = 0;
            for (RequiresFlag f: s)
                v |= f.value;
            return v;
        }

        RequiresFlag(int value) {
            this.value = value;
        }

        public final int value;
    }

    /** Flags for ExportsDirective. */
    public enum ExportsFlag {
        SYNTHETIC(0x1000),
        MANDATED(0x8000);

        // overkill? move to ClassWriter?
        public static int value(Set<ExportsFlag> s) {
            int v = 0;
            for (ExportsFlag f: s)
                v |= f.value;
            return v;
        }

        ExportsFlag(int value) {
            this.value = value;
        }

        public final int value;
    }

    /**
     * 'exports' Package ';'
     * 'exports' Package 'to' ModuleList ';'
     */
    public static class ExportsDirective extends Directive
            implements ModuleElement.ExportsDirective {
        public final PackageSymbol packge;
        public final JCList<ModuleSymbol> modules;
        public final Set<Directive.ExportsFlag> flags;

        public ExportsDirective(PackageSymbol packge, JCList<ModuleSymbol> modules) {
            this(packge, modules, EnumSet.noneOf(Directive.ExportsFlag.class));
        }

        public ExportsDirective(PackageSymbol packge, JCList<ModuleSymbol> modules, Set<Directive.ExportsFlag> flags) {
            this.packge = packge;
            this.modules = modules;
            this.flags = flags;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public ModuleElement.DirectiveKind getKind() {
            return ModuleElement.DirectiveKind.EXPORTS;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public PackageSymbol getPackage() {
            return packge;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public java.util.List<ModuleSymbol> getTargetModules() {
            return modules == null
                    ? null
                    : Collections.unmodifiableList(modules);
        }

        @Override
        public String toString() {
            if (modules == null)
                return "Exports[" + packge + "]";
            else
                return "Exports[" + packge + ":" + modules + "]";
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(DirectiveVisitor<R, P> v, P p) {
            return v.visitExports(this, p);
        }
    }

    /** Flags for OpensDirective. */
    public enum OpensFlag {
        SYNTHETIC(0x1000),
        MANDATED(0x8000);

        // overkill? move to ClassWriter?
        public static int value(Set<OpensFlag> s) {
            int v = 0;
            for (OpensFlag f: s)
                v |= f.value;
            return v;
        }

        OpensFlag(int value) {
            this.value = value;
        }

        public final int value;
    }

    /**
     * 'opens' Package ';'
     * 'opens' Package 'to' ModuleList ';'
     */
    public static class OpensDirective extends Directive
            implements ModuleElement.OpensDirective {
        public final PackageSymbol packge;
        public final JCList<ModuleSymbol> modules;
        public final Set<Directive.OpensFlag> flags;

        public OpensDirective(PackageSymbol packge, JCList<ModuleSymbol> modules) {
            this(packge, modules, EnumSet.noneOf(Directive.OpensFlag.class));
        }

        public OpensDirective(PackageSymbol packge, JCList<ModuleSymbol> modules, Set<Directive.OpensFlag> flags) {
            this.packge = packge;
            this.modules = modules;
            this.flags = flags;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public ModuleElement.DirectiveKind getKind() {
            return ModuleElement.DirectiveKind.OPENS;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public PackageSymbol getPackage() {
            return packge;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public java.util.List<ModuleSymbol> getTargetModules() {
            return modules == null
                    ? null
                    : Collections.unmodifiableList(modules);
        }

        @Override
        public String toString() {
            if (modules == null)
                return "Opens[" + packge + "]";
            else
                return "Opens[" + packge + ":" + modules + "]";
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(DirectiveVisitor<R, P> v, P p) {
            return v.visitOpens(this, p);
        }
    }

    /**
     * 'provides' ServiceName 'with' QualifiedIdentifer ';'
     */
    public static class ProvidesDirective extends Directive
            implements ModuleElement.ProvidesDirective {
        public final ClassSymbol service;
        public final JCList<ClassSymbol> impls;

        public ProvidesDirective(ClassSymbol service, JCList<ClassSymbol> impls) {
            this.service = service;
            this.impls = impls;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public ModuleElement.DirectiveKind getKind() {
            return ModuleElement.DirectiveKind.PROVIDES;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public ClassSymbol getService() {
            return service;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public JCList<ClassSymbol> getImplementations() {
            return impls;
        }

        @Override
        public String toString() {
            return "Provides[" + service + "," + impls + "]";
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(DirectiveVisitor<R, P> v, P p) {
            return v.visitProvides(this, p);
        }

        // TODO: delete?
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Directive.ProvidesDirective)) {
                return false;
            }
            Directive.ProvidesDirective other = (Directive.ProvidesDirective)obj;
            return service == other.service && impls.equals(other.impls);
        }

        // TODO: delete?
        @Override
        public int hashCode() {
            return service.hashCode() * 31 + impls.hashCode() * 37;
        }
    }

    /**
     * 'requires' ('static' | 'transitive')* ModuleName ';'
     */
    public static class RequiresDirective extends Directive
            implements ModuleElement.RequiresDirective {
        public final ModuleSymbol module;
        public final Set<Directive.RequiresFlag> flags;

        public RequiresDirective(ModuleSymbol module) {
            this(module, EnumSet.noneOf(Directive.RequiresFlag.class));
        }

        public RequiresDirective(ModuleSymbol module, Set<Directive.RequiresFlag> flags) {
            this.module = module;
            this.flags = flags;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public ModuleElement.DirectiveKind getKind() {
            return ModuleElement.DirectiveKind.REQUIRES;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public boolean isStatic() {
            return flags.contains(Directive.RequiresFlag.STATIC_PHASE);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public boolean isTransitive() {
            return flags.contains(Directive.RequiresFlag.TRANSITIVE);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public ModuleSymbol getDependency() {
            return module;
        }

        @Override
        public String toString() {
            return "Requires[" + flags + "," + module + "]";
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(DirectiveVisitor<R, P> v, P p) {
            return v.visitRequires(this, p);
        }
    }

    /**
     * 'uses' ServiceName ';'
     */
    public static class UsesDirective extends Directive
            implements ModuleElement.UsesDirective {
        public final ClassSymbol service;

        public UsesDirective(ClassSymbol service) {
            this.service = service;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public ModuleElement.DirectiveKind getKind() {
            return ModuleElement.DirectiveKind.USES;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public ClassSymbol getService() {
            return service;
        }

        @Override
        public String toString() {
            return "Uses[" + service + "]";
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(DirectiveVisitor<R, P> v, P p) {
            return v.visitUses(this, p);
        }

        // TODO: delete?
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Directive.UsesDirective)) {
                return false;
            }
            Directive.UsesDirective other = (Directive.UsesDirective)obj;
            return service == other.service;
        }

        // TODO: delete?
        @Override
        public int hashCode() {
            return service.hashCode() * 31;
        }
    }
}
