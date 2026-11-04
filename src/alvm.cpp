#include <algorithm>
#include <cctype>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <limits>
#include <map>
#include <memory>
#include <optional>
#include <queue>
#include <stdexcept>
#include <string>
#include <utility>
#include <variant>
#include <vector>

namespace al {
struct Error : std::runtime_error { using std::runtime_error::runtime_error; };
struct Token { std::string text; int line=1, col=1; };

static std::vector<Token> lex(const std::string& src) {
  std::vector<Token> out; size_t i=0; int line=1,col=1;
  auto advance=[&](){ char c=src[i++]; if(c=='\n'){++line;col=1;}else ++col; return c; };
  while(i<src.size()) {
    char c=src[i];
    if(std::isspace(static_cast<unsigned char>(c))){advance();continue;}
    if(c=='/' && i+1<src.size() && src[i+1]=='/'){while(i<src.size()&&src[i]!='\n')advance();continue;}
    if(c=='/' && i+1<src.size() && src[i+1]=='*'){int sl=line,sc=col;advance();advance();bool closed=false;while(i<src.size()){if(src[i]=='*'&&i+1<src.size()&&src[i+1]=='/'){advance();advance();closed=true;break;}advance();}if(!closed)throw Error("Unterminated block comment at "+std::to_string(sl)+":"+std::to_string(sc));continue;}
    int sl=line,sc=col; std::string t;
    if(std::isalpha(static_cast<unsigned char>(c))||c=='_'){while(i<src.size()&&(std::isalnum(static_cast<unsigned char>(src[i]))||src[i]=='_'))t+=advance();}
    else if(std::isdigit(static_cast<unsigned char>(c))){while(i<src.size()&&std::isdigit(static_cast<unsigned char>(src[i])))t+=advance();}
    else if(c=='"'){advance();bool closed=false;while(i<src.size()){char x=advance();if(x=='"'){closed=true;break;}if(x=='\\'){if(i>=src.size())break;char e=advance();switch(e){case 'n':t+='\n';break;case 't':t+='\t';break;case 'r':t+='\r';break;case '"':t+='"';break;case '\\':t+='\\';break;default:throw Error("Unknown string escape at "+std::to_string(line)+":"+std::to_string(col));}}else t+=x;}if(!closed)throw Error("Unterminated string at "+std::to_string(sl)+":"+std::to_string(sc));t="\x01"+t;}
    else { static const std::vector<std::string> two={"==","!=","<=",">=","&&","||"}; bool matched=false;for(const auto& op:two){if(src.compare(i,op.size(),op)==0){advance();advance();t=op;matched=true;break;}}if(!matched){if(std::string("{}();=+-*/%!<>.[],:").find(c)==std::string::npos)throw Error("Unexpected character at "+std::to_string(sl)+":"+std::to_string(sc));t+=advance();}}
    out.push_back({t,sl,sc});
  }
  out.push_back({"<eof>",line,col});return out;
}

enum class Type : int { Int, String, Bool, IntArray, StringArray, BoolArray, Void, Unknown, UserBase=100 };
static std::vector<std::string> userTypeNames;
static std::map<std::string,Type> userTypeMap;
static Type registerUserType(const std::string& name){auto f=userTypeMap.find(name);if(f!=userTypeMap.end())return f->second;Type t=(Type)(100+(int)userTypeNames.size());userTypeNames.push_back(name);userTypeMap[name]=t;return t;}
static bool isUserType(Type t){return (int)t>=100&&(int)t<100+(int)userTypeNames.size();}
static std::string typeName(Type t){switch(t){case Type::Int:return"int";case Type::String:return"string";case Type::Bool:return"boolean";case Type::IntArray:return"int[]";case Type::StringArray:return"string[]";case Type::BoolArray:return"boolean[]";case Type::Void:return"void";default:if(isUserType(t))return userTypeNames[(int)t-100];return"unknown";}}
static bool isArray(Type t){return t==Type::IntArray||t==Type::StringArray||t==Type::BoolArray;}
static Type arrayOf(Type t){if(t==Type::Int)return Type::IntArray;if(t==Type::String)return Type::StringArray;if(t==Type::Bool)return Type::BoolArray;return Type::Unknown;}
static Type elementOf(Type t){if(t==Type::IntArray)return Type::Int;if(t==Type::StringArray)return Type::String;if(t==Type::BoolArray)return Type::Bool;return Type::Unknown;}
struct Expr {
  enum Kind { Integer, String, Boolean, Variable, This, NewObject, FieldAccess, MethodCall, Unary, Binary, ArrayLiteral, Index, Length, Call } kind;
  Token token; int64_t number=0; bool boolean=false; std::string text; std::unique_ptr<Expr> left,right; std::vector<std::unique_ptr<Expr>> elements; Type inferred=Type::Unknown; std::string resolvedFunction; int resolvedField=-1;
};
struct Stmt {
  enum Kind { Block, Declare, Assign, ArrayAssign, FieldAssign, ExprStatement, Print, Return, If, While, DoWhile, For, Foreach, Switch, Break, Continue } kind;
  Token token; std::string name; Type declared=Type::Unknown;
  std::unique_ptr<Expr> expr, condition, incrementExpr;
  std::unique_ptr<Stmt> first, second, third;
  std::vector<std::unique_ptr<Stmt>> children;
  std::vector<std::unique_ptr<Expr>> caseValues;
  std::vector<std::vector<std::unique_ptr<Stmt>>> caseStatements;
  std::vector<bool> caseDefault;
};
struct Parameter { std::string name; Type type=Type::Unknown; };
struct Function { std::string name; std::string symbol; std::string ownerClass; Type returnType=Type::Void; bool isConstructor=false; std::vector<Parameter> params; std::unique_ptr<Stmt> body; };
struct Field { std::string name; Type type=Type::Unknown; };
struct ClassDecl { std::string name; std::string baseClass; std::vector<Field> fields; std::vector<Function> methods; };
struct Program { std::string name; std::vector<ClassDecl> classes; std::vector<Function> functions; };

class Parser {
  std::vector<Token> t; size_t p=0;
  const Token& tok()const{return t[p];} std::string cur()const{return tok().text;}
  bool eat(const std::string& s){if(cur()==s){++p;return true;}return false;}
  Token take(){return t[p++];}
  void need(const std::string& s){if(!eat(s))throw Error("Expected '"+s+"' at "+std::to_string(tok().line)+":"+std::to_string(tok().col));}
  std::unique_ptr<Expr> node(Expr::Kind k,Token tk){auto x=std::make_unique<Expr>();x->kind=k;x->token=std::move(tk);return x;}
  std::unique_ptr<Stmt> stmtNode(Stmt::Kind k,Token tk){auto x=std::make_unique<Stmt>();x->kind=k;x->token=std::move(tk);return x;}
  Type parseType(){Type t;if(eat("int"))t=Type::Int;else if(eat("string"))t=Type::String;else if(eat("boolean"))t=Type::Bool;else if(eat("void"))t=Type::Void;else{auto f=userTypeMap.find(cur());if(f==userTypeMap.end())throw Error("Unknown type at "+std::to_string(tok().line)+":"+std::to_string(tok().col));t=f->second;take();}if(eat("[")){need("]");t=arrayOf(t);if(t==Type::Unknown)throw Error("Unsupported array element type");}return t;}
  std::unique_ptr<Expr> primary(){Token tk=tok();std::unique_ptr<Expr> x;if(cur().size()&&std::isdigit(static_cast<unsigned char>(cur()[0]))){take();x=node(Expr::Integer,tk);try{x->number=std::stoll(tk.text);}catch(...){throw Error("Integer literal out of range at "+std::to_string(tk.line)+":"+std::to_string(tk.col));}}else if(cur().size()&&cur()[0]=='\x01'){take();x=node(Expr::String,tk);x->text=tk.text.substr(1);}else if(eat("true")||eat("false")){x=node(Expr::Boolean,tk);x->boolean=tk.text=="true";}else if(eat("(")){x=expression();need(")");}else if(eat("[")){x=node(Expr::ArrayLiteral,tk);if(cur()!="]"){do{x->elements.push_back(expression());}while(eat(","));}need("]");}else if(eat("new")){Token cls=take();auto it=userTypeMap.find(cls.text);if(it==userTypeMap.end())throw Error("Unknown class: "+cls.text);x=node(Expr::NewObject,tk);x->text=cls.text;need("(");if(cur()!=")"){do{x->elements.push_back(expression());}while(eat(","));}need(")");}else if(eat("this")){x=node(Expr::This,tk);x->text="this";}else if(std::isalpha(static_cast<unsigned char>(cur()[0]))||cur()[0]=='_'){take();if(eat("(")){x=node(Expr::Call,tk);x->text=tk.text;if(cur()!=")"){do{x->elements.push_back(expression());}while(eat(","));}need(")");}else{x=node(Expr::Variable,tk);x->text=tk.text;}}else throw Error("Expected expression at "+std::to_string(tok().line)+":"+std::to_string(tok().col));while(true){if(eat("[")){Token it=tok();auto idx=node(Expr::Index,it);idx->left=std::move(x);idx->right=expression();need("]");x=std::move(idx);}else if(eat(".")){Token it=take();if(it.text=="length"){auto len=node(Expr::Length,it);len->left=std::move(x);x=std::move(len);}else if(eat("(")){auto call=node(Expr::MethodCall,it);call->text=it.text;call->left=std::move(x);if(cur()!=")"){do{call->elements.push_back(expression());}while(eat(","));}need(")");x=std::move(call);}else{auto field=node(Expr::FieldAccess,it);field->left=std::move(x);field->text=it.text;x=std::move(field);}}else break;}return x;}
  std::unique_ptr<Expr> unary(){if(cur()=="!"||cur()=="-"){Token tk=take();auto x=node(Expr::Unary,tk);x->text=tk.text;x->left=unary();return x;}return primary();}
  template<class Next> std::unique_ptr<Expr> binary(Next next,const std::vector<std::string>& ops){auto x=(this->*next)();while(std::find(ops.begin(),ops.end(),cur())!=ops.end()){Token tk=take();auto n=node(Expr::Binary,tk);n->text=tk.text;n->left=std::move(x);n->right=(this->*next)();x=std::move(n);}return x;}
  std::unique_ptr<Expr> factor(){return binary(&Parser::unary,{"*","/","%"});}
  std::unique_ptr<Expr> term(){return binary(&Parser::factor,{"+","-"});}
  std::unique_ptr<Expr> compare(){return binary(&Parser::term,{"<",">","<=",">="});}
  std::unique_ptr<Expr> equality(){return binary(&Parser::compare,{"==","!="});}
  std::unique_ptr<Expr> logicalAnd(){return binary(&Parser::equality,{"&&"});}
  std::unique_ptr<Expr> logicalOr(){return binary(&Parser::logicalAnd,{"||"});}
  std::unique_ptr<Expr> expression(){return logicalOr();}
  bool isType(){return cur()=="int"||cur()=="string"||cur()=="boolean"||cur()=="var"||userTypeMap.count(cur())!=0;}
  std::unique_ptr<Stmt> block(){Token tk=tok();need("{");auto b=stmtNode(Stmt::Block,tk);while(cur()!="}"&&cur()!="<eof>")b->children.push_back(statement());need("}");return b;}
  std::unique_ptr<Stmt> declaration(bool semicolon=true){Token tk=tok();Type ty=Type::Unknown;if(eat("var"))ty=Type::Unknown;else ty=parseType();auto s=stmtNode(Stmt::Declare,tk);s->declared=ty;s->name=take().text;if(s->name=="<eof>")throw Error("Expected variable name");need("=");s->expr=expression();if(semicolon)need(";");return s;}
  std::unique_ptr<Stmt> assignment(bool semicolon=true){Token tk=tok();auto s=stmtNode(Stmt::Assign,tk);s->name=take().text;if(eat("[")){s->kind=Stmt::ArrayAssign;s->condition=expression();need("]");}else if(eat(".")){Token field=take();s->kind=Stmt::FieldAssign;s->name=field.text;auto receiver=node(tk.text=="this"?Expr::This:Expr::Variable,tk);receiver->text=tk.text;s->condition=std::move(receiver);}need("=");s->expr=expression();if(semicolon)need(";");return s;}
  bool assignmentAhead()const{if(p+1<t.size()&&t[p+1].text=="=")return true;if(p+1<t.size()&&t[p+1].text==".")return p+3<t.size()&&t[p+3].text=="=";if(p+1<t.size()&&t[p+1].text=="["){int depth=0;for(size_t q=p+1;q<t.size();++q){if(t[q].text=="[")++depth;else if(t[q].text=="]"&&--depth==0)return q+1<t.size()&&t[q+1].text=="=";}}return false;}
  std::unique_ptr<Stmt> statement(){Token tk=tok();if(cur()=="{")return block();if(isType())return declaration();if(eat("AL")){auto s=stmtNode(Stmt::Print,tk);need(".");need("print");need("(");s->expr=expression();need(")");need(";");return s;}if(eat("return")){auto s=stmtNode(Stmt::Return,tk);if(!eat(";")){s->expr=expression();need(";");}return s;}if(eat("break")){auto s=stmtNode(Stmt::Break,tk);need(";");return s;}if(eat("continue")){auto s=stmtNode(Stmt::Continue,tk);need(";");return s;}if(eat("if")){auto s=stmtNode(Stmt::If,tk);need("(");s->condition=expression();need(")");s->first=block();if(eat("else"))s->second=block();return s;}if(eat("while")){auto s=stmtNode(Stmt::While,tk);need("(");s->condition=expression();need(")");s->first=block();return s;}if(eat("do")){auto s=stmtNode(Stmt::DoWhile,tk);s->first=block();need("while");need("(");s->condition=expression();need(")");need(";");return s;}if(eat("for")){auto s=stmtNode(Stmt::For,tk);need("(");if(isType())s->first=declaration(false);else s->first=assignment(false);need(";");s->condition=expression();need(";");s->second=assignment(false);need(")");s->third=block();return s;}if(eat("foreach")){auto s=stmtNode(Stmt::Foreach,tk);need("(");if(eat("var"))s->declared=Type::Unknown;else s->declared=parseType();s->name=take().text;if(s->name=="<eof>")throw Error("Expected foreach variable name");need(":");s->expr=expression();need(")");s->first=block();return s;}if(eat("switch")){auto s=stmtNode(Stmt::Switch,tk);need("(");s->expr=expression();need(")");need("{");bool sawDefault=false;while(cur()!="}"&&cur()!="<eof>"){bool isDefault=eat("default");std::unique_ptr<Expr> value;if(!isDefault){need("case");value=expression();}else{if(sawDefault)throw Error("Duplicate default label");sawDefault=true;}need(":");std::vector<std::unique_ptr<Stmt>> body;while(cur()!="case"&&cur()!="default"&&cur()!="}"&&cur()!="<eof>")body.push_back(statement());s->caseValues.push_back(std::move(value));s->caseStatements.push_back(std::move(body));s->caseDefault.push_back(isDefault);}need("}");return s;}if(std::isalpha(static_cast<unsigned char>(cur()[0]))||cur()[0]=='_'){if(assignmentAhead())return assignment();auto s=stmtNode(Stmt::ExprStatement,tk);s->expr=expression();need(";");return s;}throw Error("Unsupported statement at "+std::to_string(tok().line)+":"+std::to_string(tok().col));}
public:
  explicit Parser(const std::string& src):t(lex(src)){}
  Function parseFunction(const std::string& owner){while(cur()=="public"||cur()=="private"||cur()=="protected"||cur()=="static")take();Function fn;fn.returnType=parseType();fn.name=take().text;fn.ownerClass=owner;if(fn.name=="<eof>")throw Error("Expected function name");need("(");if(cur()!=")"){do{Type pt=parseType();if(pt==Type::Void)throw Error("Function parameter cannot have type void");Parameter param;param.type=pt;param.name=take().text;if(param.name=="<eof>")throw Error("Expected parameter name");fn.params.push_back(std::move(param));}while(eat(","));}need(")");fn.body=block();return fn;}
  ClassDecl parseClass(){need("class");ClassDecl cls;cls.name=take().text;if(userTypeMap.find(cls.name)==userTypeMap.end())registerUserType(cls.name);if(eat("extends")){cls.baseClass=take().text;if(userTypeMap.find(cls.baseClass)==userTypeMap.end())throw Error("Unknown base class: "+cls.baseClass);if(cls.baseClass==cls.name)throw Error("A class cannot extend itself");}need("{");while(cur()!="}"&&cur()!="<eof>"){while(cur()=="public"||cur()=="private"||cur()=="protected"||cur()=="static")take();if(cur()==cls.name&&p+1<t.size()&&t[p+1].text=="("){take();Function ctor;ctor.ownerClass=cls.name;ctor.name="$ctor";ctor.returnType=Type::Void;ctor.isConstructor=true;need("(");if(cur()!=")"){do{Type pt=parseType();if(pt==Type::Void)throw Error("Constructor parameter cannot have type void");Parameter param;param.type=pt;param.name=take().text;ctor.params.push_back(std::move(param));}while(eat(","));}need(")");ctor.body=block();cls.methods.push_back(std::move(ctor));continue;}Type ty=parseType();Token name=take();if(name.text=="<eof>")throw Error("Expected class member name");if(eat("(")){Function fn;fn.ownerClass=cls.name;fn.name=name.text;fn.returnType=ty;if(cur()!=")"){do{Type pt=parseType();if(pt==Type::Void)throw Error("Function parameter cannot have type void");Parameter param;param.type=pt;param.name=take().text;fn.params.push_back(std::move(param));}while(eat(","));}need(")");fn.body=block();cls.methods.push_back(std::move(fn));}else{if(ty==Type::Void)throw Error("Class field cannot have type void");need(";");for(const auto& field:cls.fields)if(field.name==name.text)throw Error("Duplicate field: "+name.text);cls.fields.push_back({name.text,ty});}}need("}");if(std::none_of(cls.methods.begin(),cls.methods.end(),[](const Function& fn){return fn.isConstructor;})){Function ctor;ctor.name="$ctor";ctor.ownerClass=cls.name;ctor.returnType=Type::Void;ctor.isConstructor=true;ctor.body=stmtNode(Stmt::Block,tok());cls.methods.push_back(std::move(ctor));}return cls;}
  Program parse(){if(!(eat("room")||eat("class")))throw Error("Program must start with room");Program out;out.name=take().text;need("{");userTypeNames.clear();userTypeMap.clear();for(size_t q=p;q+1<t.size();++q)if(t[q].text=="class"&&t[q+1].text!="<eof>")registerUserType(t[q+1].text);while(cur()!="}"&&cur()!="<eof>"){if(cur()=="class")out.classes.push_back(parseClass());else out.functions.push_back(parseFunction(""));}need("}");if(cur()!="<eof>")throw Error("Unexpected token after program end: "+cur());auto mainIt=std::find_if(out.functions.begin(),out.functions.end(),[](const Function& fn){return fn.name=="main";});if(mainIt==out.functions.end())throw Error("Program must declare main()");if(mainIt!=out.functions.begin())std::iter_swap(out.functions.begin(),mainIt);return out;}
};

class SemanticAnalyzer {
  struct Signature { Type result=Type::Void; std::vector<Type> params; std::string ownerClass; std::string symbol; };
  struct ClassInfo { Type type=Type::Unknown; Type baseType=Type::Unknown; std::map<std::string,int> fieldSlots; std::vector<Type> fieldTypes; std::map<std::string,Signature> methods; Signature constructor; bool hasConstructor=false; };
  std::map<std::string,Signature> signatures;
  std::map<std::string,std::vector<Signature>> functionOverloads;
  std::map<std::string,ClassInfo> classes;
  std::vector<std::map<std::string,Type>> scopes;
  Type currentReturn=Type::Void;
  std::string currentClass;
  int loopDepth=0;
  int breakDepth=0;
  void push(){scopes.emplace_back();}
  void pop(){scopes.pop_back();}
  void declare(const std::string& n,Type t){auto& scope=scopes.back();if(scope.count(n))throw Error("Duplicate variable in scope: "+n);scope[n]=t;}
  Type lookup(const std::string& n){for(auto i=scopes.rbegin();i!=scopes.rend();++i){auto f=i->find(n);if(f!=i->end())return f->second;}throw Error("Unknown variable: "+n);}
  ClassInfo& classFor(Type t){for(auto& item:classes)if(item.second.type==t)return item.second;throw Error("Expected a class object, got "+typeName(t));}
  bool isAssignable(Type actual,Type expected)const{
    if(actual==expected)return true;
    if(!isUserType(actual)||!isUserType(expected))return false;
    for(const auto& item:classes)if(item.second.type==actual){Type base=item.second.baseType;while(base!=Type::Unknown){if(base==expected)return true;bool found=false;for(const auto& parent:classes)if(parent.second.type==base){base=parent.second.baseType;found=true;break;}if(!found)break;}return false;}
    return false;
  }
  Type checkExpr(Expr& e){
    switch(e.kind){
      case Expr::Integer:e.inferred=Type::Int;break;
      case Expr::String:e.inferred=Type::String;break;
      case Expr::Boolean:e.inferred=Type::Bool;break;
      case Expr::Variable:e.inferred=lookup(e.text);break;
      case Expr::This:if(currentClass.empty())throw Error("'this' can only be used inside an instance method");e.inferred=classes.at(currentClass).type;break;
      case Expr::NewObject:{auto it=classes.find(e.text);if(it==classes.end())throw Error("Unknown class: "+e.text);const auto& ctor=it->second.constructor;if(e.elements.size()!=ctor.params.size())throw Error("Wrong argument count for constructor "+e.text);for(size_t i=0;i<e.elements.size();++i){Type actual=checkExpr(*e.elements[i]);if(!isAssignable(actual,ctor.params[i]))throw Error("Constructor argument "+std::to_string(i+1)+" must be "+typeName(ctor.params[i]));}e.resolvedFunction=e.text+".$ctor";e.inferred=it->second.type;break;}
      case Expr::FieldAccess:{Type objectType=checkExpr(*e.left);ClassInfo& cls=classFor(objectType);auto it=cls.fieldSlots.find(e.text);if(it==cls.fieldSlots.end())throw Error("Unknown field '"+e.text+"' on "+typeName(objectType));e.resolvedField=it->second;e.inferred=cls.fieldTypes[(size_t)it->second];break;}
      case Expr::MethodCall:{Type objectType=checkExpr(*e.left);ClassInfo& cls=classFor(objectType);auto it=cls.methods.find(e.text);if(it==cls.methods.end())throw Error("Unknown method '"+e.text+"' on "+typeName(objectType));const auto& sig=it->second;if(e.elements.size()!=sig.params.size())throw Error("Wrong argument count for method "+e.text);for(size_t i=0;i<e.elements.size();++i){Type actual=checkExpr(*e.elements[i]);if(!isAssignable(actual,sig.params[i]))throw Error("Argument "+std::to_string(i+1)+" of method "+e.text+" must be "+typeName(sig.params[i]));}e.resolvedFunction=sig.ownerClass+"."+e.text;e.inferred=sig.result;break;}
      case Expr::Call:{if(e.text=="main")throw Error("main() cannot be called as a normal function");auto found=functionOverloads.find(e.text);if(found==functionOverloads.end())throw Error("Unknown function: "+e.text);std::vector<Type> actual;for(auto& arg:e.elements)actual.push_back(checkExpr(*arg));const Signature* best=nullptr;int bestScore=-1;bool ambiguous=false;for(const auto& candidate:found->second){if(candidate.params.size()!=actual.size())continue;bool viable=true;int score=0;for(size_t i=0;i<actual.size();++i){if(!isAssignable(actual[i],candidate.params[i])){viable=false;break;}if(actual[i]==candidate.params[i])++score;}if(viable&&score>bestScore){best=&candidate;bestScore=score;ambiguous=false;}else if(viable&&score==bestScore){ambiguous=true;}}if(!best)throw Error("No matching overload for function: "+e.text);if(ambiguous)throw Error("Ambiguous overload for function: "+e.text);e.resolvedFunction=best->symbol;e.inferred=best->result;break;}
      case Expr::ArrayLiteral:{if(e.elements.empty())throw Error("An array literal must contain at least one element so its type can be inferred");Type item=checkExpr(*e.elements[0]);if(item!=Type::Int&&item!=Type::String&&item!=Type::Bool)throw Error("Array elements must be int, string or boolean");for(size_t i=1;i<e.elements.size();++i)if(checkExpr(*e.elements[i])!=item)throw Error("Array literal elements must all have the same type");e.inferred=arrayOf(item);break;}
      case Expr::Index:{Type base=checkExpr(*e.left);if(!isArray(base))throw Error("Indexing requires an array");if(checkExpr(*e.right)!=Type::Int)throw Error("Array index must be int");e.inferred=elementOf(base);break;}
      case Expr::Length:{Type base=checkExpr(*e.left);if(!isArray(base))throw Error(".length requires an array");e.inferred=Type::Int;break;}
      case Expr::Unary:{Type x=checkExpr(*e.left);if(e.text=="-"&&x!=Type::Int)throw Error("Unary '-' requires int");if(e.text=="!"&&x!=Type::Bool)throw Error("Unary '!' requires boolean");e.inferred=e.text=="-"?Type::Int:Type::Bool;break;}
      case Expr::Binary:{Type x=checkExpr(*e.left),y=checkExpr(*e.right);const auto& op=e.text;if(op=="+"&&x==Type::String&&y==Type::String)e.inferred=Type::String;else if(op=="+"||op=="-"||op=="*"||op=="/"||op=="%"){if(x!=Type::Int||y!=Type::Int)throw Error("Operator '"+op+"' requires int operands");e.inferred=Type::Int;}else if(op=="<"||op==">"||op=="<="||op==">="){if(x!=Type::Int||y!=Type::Int)throw Error("Comparison '"+op+"' requires int operands");e.inferred=Type::Bool;}else if(op=="=="||op=="!="){if(x!=y&&!(isAssignable(x,y)||isAssignable(y,x)))throw Error("Equality operands must have compatible types");e.inferred=Type::Bool;}else if(op=="&&"||op=="||"){if(x!=Type::Bool||y!=Type::Bool)throw Error("Logical operator requires boolean operands");e.inferred=Type::Bool;}else throw Error("Unsupported operator: "+op);break;}
    }
    return e.inferred;
  }
  void checkStmt(Stmt& s){
    switch(s.kind){
      case Stmt::Block:push();for(auto& child:s.children)checkStmt(*child);pop();break;
      case Stmt::Declare:{Type rhs=checkExpr(*s.expr);Type actual=s.declared==Type::Unknown?rhs:s.declared;if(!isAssignable(rhs,actual))throw Error("Cannot assign "+typeName(rhs)+" to "+typeName(actual)+" variable '"+s.name+"'");declare(s.name,actual);break;}
      case Stmt::Assign:{Type lhs=lookup(s.name),rhs=checkExpr(*s.expr);if(!isAssignable(rhs,lhs))throw Error("Cannot assign "+typeName(rhs)+" to "+typeName(lhs)+" variable '"+s.name+"'");break;}
      case Stmt::ArrayAssign:{Type arr=lookup(s.name);if(!isArray(arr))throw Error("Indexed assignment requires an array variable: "+s.name);if(checkExpr(*s.condition)!=Type::Int)throw Error("Array index must be int");Type rhs=checkExpr(*s.expr);if(rhs!=elementOf(arr))throw Error("Cannot store "+typeName(rhs)+" in "+typeName(arr));break;}
      case Stmt::FieldAssign:{Type objectType=checkExpr(*s.condition);ClassInfo& cls=classFor(objectType);auto it=cls.fieldSlots.find(s.name);if(it==cls.fieldSlots.end())throw Error("Unknown field '"+s.name+"' on "+typeName(objectType));Type expected=cls.fieldTypes[(size_t)it->second],actual=checkExpr(*s.expr);if(!isAssignable(actual,expected))throw Error("Cannot assign "+typeName(actual)+" to field "+s.name+" of type "+typeName(expected));s.incrementExpr=std::make_unique<Expr>();s.incrementExpr->number=it->second;break;}
      case Stmt::ExprStatement:checkExpr(*s.expr);break;
      case Stmt::Print:if(checkExpr(*s.expr)==Type::Void)throw Error("Cannot print a void function result");break;
      case Stmt::Return:{if(currentReturn==Type::Void){if(s.expr)throw Error("Void function cannot return a value");}else{if(!s.expr)throw Error("Non-void function must return a value");Type actual=checkExpr(*s.expr);if(!isAssignable(actual,currentReturn))throw Error("Return type mismatch: expected "+typeName(currentReturn)+", got "+typeName(actual));}break;}
      case Stmt::If:if(checkExpr(*s.condition)!=Type::Bool)throw Error("if condition must be boolean");checkStmt(*s.first);if(s.second)checkStmt(*s.second);break;
      case Stmt::While:if(checkExpr(*s.condition)!=Type::Bool)throw Error("while condition must be boolean");++loopDepth;++breakDepth;checkStmt(*s.first);--breakDepth;--loopDepth;break;
      case Stmt::DoWhile:++loopDepth;++breakDepth;checkStmt(*s.first);--breakDepth;--loopDepth;if(checkExpr(*s.condition)!=Type::Bool)throw Error("do-while condition must be boolean");break;
      case Stmt::For:push();checkStmt(*s.first);if(checkExpr(*s.condition)!=Type::Bool)throw Error("for condition must be boolean");++loopDepth;++breakDepth;checkStmt(*s.third);--breakDepth;--loopDepth;checkStmt(*s.second);pop();break;
      case Stmt::Foreach:{Type arrayType=checkExpr(*s.expr);if(!isArray(arrayType))throw Error("foreach requires an array expression");Type itemType=elementOf(arrayType);Type declared=s.declared==Type::Unknown?itemType:s.declared;if(declared!=itemType)throw Error("foreach variable type "+typeName(declared)+" does not match array element type "+typeName(itemType));push();declare(s.name,declared);++loopDepth;++breakDepth;checkStmt(*s.first);--breakDepth;--loopDepth;pop();break;}case Stmt::Switch:{Type switchType=checkExpr(*s.expr);if(switchType!=Type::Int&&switchType!=Type::String&&switchType!=Type::Bool)throw Error("switch supports int, string or boolean values");bool defaultSeen=false;++breakDepth;for(size_t i=0;i<s.caseValues.size();++i){if(s.caseDefault[i]){if(defaultSeen)throw Error("Duplicate default label");defaultSeen=true;}else if(checkExpr(*s.caseValues[i])!=switchType)throw Error("switch case type must match switch expression");push();for(auto& child:s.caseStatements[i])checkStmt(*child);pop();}--breakDepth;break;}
      case Stmt::Break:if(breakDepth==0)throw Error("break is only valid inside a loop or switch");break;
      case Stmt::Continue:if(loopDepth==0)throw Error("continue is only valid inside a loop");break;
    }
  }
  bool alwaysReturns(const Stmt& s)const{if(s.kind==Stmt::Return)return true;if(s.kind==Stmt::Block){for(const auto& child:s.children)if(alwaysReturns(*child))return true;return false;}if(s.kind==Stmt::If&&s.second)return alwaysReturns(*s.first)&&alwaysReturns(*s.second);return false;}
  Signature signature(const Function& fn){Signature sig;sig.result=fn.returnType;sig.ownerClass=fn.ownerClass;sig.symbol=fn.symbol;for(const auto& param:fn.params)sig.params.push_back(param.type);return sig;}
  void analyzeFunction(Function& fn){currentClass=fn.ownerClass;currentReturn=fn.returnType;push();if(!currentClass.empty())declare("this",classes.at(currentClass).type);for(const auto& param:fn.params)declare(param.name,param.type);checkStmt(*fn.body);pop();if(fn.returnType!=Type::Void&&!alwaysReturns(*fn.body))throw Error("Non-void function may finish without returning a value: "+fn.name);}
public:
  void analyze(Program& p){
    if(p.functions.empty()||p.functions[0].name!="main")throw Error("Missing main() entry function");
    if(p.functions[0].returnType!=Type::Void||!p.functions[0].params.empty())throw Error("main() must be declared as void main() with no parameters");
    for(const auto& cls:p.classes){
      ClassInfo info;info.type=userTypeMap.at(cls.name);
      if(!cls.baseClass.empty()){
        auto parent=classes.find(cls.baseClass);
        if(parent==classes.end())throw Error("Base class '"+cls.baseClass+"' must be declared before derived class '"+cls.name+"'");
        info.baseType=parent->second.type;info.fieldSlots=parent->second.fieldSlots;info.fieldTypes=parent->second.fieldTypes;info.methods=parent->second.methods;
      }
      for(const auto& field:cls.fields){
        if(field.type==Type::Void||isArray(field.type)||isUserType(field.type))throw Error("Class fields currently support scalar int, string or boolean types only");
        if(info.fieldSlots.count(field.name))throw Error("Field '"+field.name+"' conflicts with an inherited field in "+cls.name);
        info.fieldSlots[field.name]=(int)info.fieldTypes.size();info.fieldTypes.push_back(field.type);
      }
      if(!classes.emplace(cls.name,std::move(info)).second)throw Error("Duplicate class declaration: "+cls.name);
    }
    for(const auto& cls:p.classes){
      auto& info=classes.at(cls.name);
      if(!cls.baseClass.empty())info.methods=classes.at(cls.baseClass).methods;
      for(const auto& method:cls.methods){
        Signature sig=signature(method);
        if(method.isConstructor){
          if(info.hasConstructor)throw Error("Duplicate constructor in class "+cls.name);
          if(!cls.baseClass.empty()&&classes.at(cls.baseClass).constructor.params.size()!=0)throw Error("Base class "+cls.baseClass+" has no zero-argument constructor; explicit super constructor calls are not supported yet");
          info.constructor=sig;info.hasConstructor=true;
          std::string key=cls.name+".$ctor";if(signatures.count(key))throw Error("Duplicate constructor signature");signatures.emplace(key,std::move(sig));
          continue;
        }
        if(info.methods.count(method.name))throw Error("Method '"+method.name+"' conflicts with inherited method in "+cls.name+"; overriding is not implemented yet");
        info.methods.emplace(method.name,sig);
        std::string key=cls.name+"."+method.name;if(signatures.count(key))throw Error("Duplicate method signature");signatures.emplace(key,std::move(sig));
      }
    }
    for(auto& fn:p.functions){Signature sig=signature(fn);std::string symbol=fn.name;if(fn.name!="main"){symbol+="$";for(const auto& param:fn.params){std::string encoded=typeName(param.type);symbol+=std::to_string(encoded.size());symbol+="_";symbol+=encoded;}}fn.symbol=symbol;sig.symbol=symbol;auto& overloads=functionOverloads[fn.name];for(const auto& prior:overloads)if(prior.params==sig.params)throw Error("Duplicate function signature: "+fn.name);overloads.push_back(std::move(sig));}
    for(auto& fn:p.functions)analyzeFunction(fn);
    for(auto& cls:p.classes)for(auto& method:cls.methods)analyzeFunction(method);
    currentClass.clear();
  }
};
enum class Op : uint8_t { PushInt=1,PushString,PushBool,Load,Store,Add,Sub,Mul,Div,Mod,Neg,Not,Eq,Ne,Lt,Gt,Le,Ge,And,Or,Jump,JumpFalse,Print,Pop,Halt,NewArray,ArrayGet,ArraySet,ArrayLength,Call,Ret,NewObject,GetField,SetField,Dup };
struct IR { Op op; int64_t arg=0; std::string text; };
struct ClassIR { std::string name; int32_t baseClassId=-1; std::vector<Type> fieldTypes; };
struct FunctionIR { std::string name; std::string ownerClass; int32_t ownerClassId=-1; Type returnType=Type::Void; uint32_t parameterCount=0; uint32_t localCount=0; std::vector<IR> code; };
struct IRProgram { std::vector<ClassIR> classes; std::vector<FunctionIR> functions; };
class IRGenerator {
  std::vector<IR> code; std::map<std::string,int> slots; std::map<std::string,int> functionIds, classIds;
  std::map<std::string,std::map<std::string,int>> fieldSlots;
  std::map<std::string,std::string> baseClasses;
  struct LoopPatch { int continueTarget=-1; std::vector<int> pendingContinues; };
  std::vector<LoopPatch> loopPatches;
  std::vector<std::vector<int>> breakPatches;
  int next=0;
  int emit(Op op,int64_t arg=0,std::string text={}){code.push_back({op,arg,std::move(text)});return (int)code.size()-1;}
  void expression(const Expr& e){
    switch(e.kind){
      case Expr::Integer:emit(Op::PushInt,e.number);break;
      case Expr::String:emit(Op::PushString,0,e.text);break;
      case Expr::Boolean:emit(Op::PushBool,e.boolean?1:0);break;
      case Expr::Variable:emit(Op::Load,slots.at(e.text));break;
      case Expr::This:emit(Op::Load,slots.at("this"));break;
      case Expr::NewObject:emit(Op::NewObject,classIds.at(e.text));emit(Op::Dup);for(const auto& arg:e.elements)expression(*arg);emit(Op::Call,functionIds.at(e.resolvedFunction));break;
      case Expr::FieldAccess:expression(*e.left);emit(Op::GetField,e.resolvedField);break;
      case Expr::MethodCall:expression(*e.left);for(const auto& arg:e.elements)expression(*arg);emit(Op::Call,functionIds.at(e.resolvedFunction));break;
      case Expr::Call:for(const auto& arg:e.elements)expression(*arg);emit(Op::Call,functionIds.at(e.resolvedFunction));break;
      case Expr::ArrayLiteral:for(const auto& item:e.elements)expression(*item);emit(Op::NewArray,(int64_t)e.elements.size());break;
      case Expr::Index:expression(*e.left);expression(*e.right);emit(Op::ArrayGet);break;
      case Expr::Length:expression(*e.left);emit(Op::ArrayLength);break;
      case Expr::Unary:expression(*e.left);emit(e.text=="-"?Op::Neg:Op::Not);break;
      case Expr::Binary:{
        if(e.text=="&&"){expression(*e.left);int jf=emit(Op::JumpFalse,-1);expression(*e.right);int j=emit(Op::Jump,-1);code[jf].arg=(int64_t)code.size();emit(Op::PushBool,0);code[j].arg=(int64_t)code.size();break;}
        if(e.text=="||"){expression(*e.left);int jf=emit(Op::JumpFalse,-1);emit(Op::PushBool,1);int j=emit(Op::Jump,-1);code[jf].arg=(int64_t)code.size();expression(*e.right);code[j].arg=(int64_t)code.size();break;}
        expression(*e.left);expression(*e.right);
        if(e.text=="+")emit(Op::Add);else if(e.text=="-")emit(Op::Sub);else if(e.text=="*")emit(Op::Mul);else if(e.text=="/")emit(Op::Div);else if(e.text=="%")emit(Op::Mod);else if(e.text=="==")emit(Op::Eq);else if(e.text=="!=")emit(Op::Ne);else if(e.text=="<")emit(Op::Lt);else if(e.text==">")emit(Op::Gt);else if(e.text=="<=")emit(Op::Le);else if(e.text==">=")emit(Op::Ge);
        break;
      }
    }
  }
  void statement(const Stmt& s){
    switch(s.kind){
      case Stmt::Block:{auto saved=slots;for(const auto& child:s.children)statement(*child);slots=std::move(saved);break;}
      case Stmt::Declare:{int slot=next++;slots[s.name]=slot;expression(*s.expr);emit(Op::Store,slot);break;}
      case Stmt::Assign:expression(*s.expr);emit(Op::Store,slots.at(s.name));break;
      case Stmt::ArrayAssign:emit(Op::Load,slots.at(s.name));expression(*s.condition);expression(*s.expr);emit(Op::ArraySet);break;
      case Stmt::FieldAssign:expression(*s.condition);expression(*s.expr);emit(Op::SetField,(int64_t)s.incrementExpr->number);break;
      case Stmt::ExprStatement:expression(*s.expr);if(s.expr->inferred!=Type::Void)emit(Op::Pop);break;
      case Stmt::Print:expression(*s.expr);emit(Op::Print);break;
      case Stmt::Return:if(s.expr)expression(*s.expr);emit(Op::Ret);break;
      case Stmt::If:{expression(*s.condition);int jf=emit(Op::JumpFalse,-1);statement(*s.first);if(s.second){int j=emit(Op::Jump,-1);code[jf].arg=(int64_t)code.size();statement(*s.second);code[j].arg=(int64_t)code.size();}else code[jf].arg=(int64_t)code.size();break;}
      case Stmt::While:{int start=(int)code.size();expression(*s.condition);int jf=emit(Op::JumpFalse,-1);breakPatches.emplace_back();loopPatches.push_back({start,{}});statement(*s.first);for(int j:loopPatches.back().pendingContinues)code[j].arg=start;loopPatches.pop_back();emit(Op::Jump,start);int end=(int)code.size();code[jf].arg=end;for(int j:breakPatches.back())code[j].arg=end;breakPatches.pop_back();break;}
      case Stmt::DoWhile:{int start=(int)code.size();breakPatches.emplace_back();loopPatches.push_back({-1,{}});statement(*s.first);int conditionStart=(int)code.size();for(int j:loopPatches.back().pendingContinues)code[j].arg=conditionStart;loopPatches.pop_back();expression(*s.condition);int jf=emit(Op::JumpFalse,-1);emit(Op::Jump,start);int end=(int)code.size();code[jf].arg=end;for(int j:breakPatches.back())code[j].arg=end;breakPatches.pop_back();break;}
      case Stmt::For:{auto saved=slots;statement(*s.first);int start=(int)code.size();expression(*s.condition);int jf=emit(Op::JumpFalse,-1);breakPatches.emplace_back();loopPatches.push_back({-1,{}});statement(*s.third);int incrementStart=(int)code.size();for(int j:loopPatches.back().pendingContinues)code[j].arg=incrementStart;loopPatches.pop_back();statement(*s.second);emit(Op::Jump,start);int end=(int)code.size();code[jf].arg=end;for(int j:breakPatches.back())code[j].arg=end;breakPatches.pop_back();slots=std::move(saved);break;}
      case Stmt::Foreach:{auto saved=slots;int arraySlot=next++;expression(*s.expr);emit(Op::Store,arraySlot);int indexSlot=next++;emit(Op::PushInt,0);emit(Op::Store,indexSlot);int itemSlot=next++;slots[s.name]=itemSlot;int start=(int)code.size();emit(Op::Load,indexSlot);emit(Op::Load,arraySlot);emit(Op::ArrayLength);emit(Op::Lt);int jf=emit(Op::JumpFalse,-1);emit(Op::Load,arraySlot);emit(Op::Load,indexSlot);emit(Op::ArrayGet);emit(Op::Store,itemSlot);breakPatches.emplace_back();loopPatches.push_back({-1,{}});statement(*s.first);int incrementStart=(int)code.size();for(int j:loopPatches.back().pendingContinues)code[j].arg=incrementStart;loopPatches.pop_back();emit(Op::Load,indexSlot);emit(Op::PushInt,1);emit(Op::Add);emit(Op::Store,indexSlot);emit(Op::Jump,start);int end=(int)code.size();code[jf].arg=end;for(int j:breakPatches.back())code[j].arg=end;breakPatches.pop_back();slots=std::move(saved);break;}case Stmt::Switch:{int switchSlot=next++;expression(*s.expr);emit(Op::Store,switchSlot);breakPatches.emplace_back();std::vector<int> bodyJumps(s.caseValues.size(),-1);int defaultIndex=-1;for(size_t i=0;i<s.caseValues.size();++i){if(s.caseDefault[i]){defaultIndex=(int)i;continue;}emit(Op::Load,switchSlot);expression(*s.caseValues[i]);emit(Op::Eq);int jf=emit(Op::JumpFalse,-1);bodyJumps[i]=emit(Op::Jump,-1);code[jf].arg=(int64_t)code.size();}int noMatch=emit(Op::Jump,-1);std::vector<int> bodyStarts(s.caseValues.size(),-1);for(size_t i=0;i<s.caseValues.size();++i){bodyStarts[i]=(int)code.size();if(bodyJumps[i]>=0)code[bodyJumps[i]].arg=bodyStarts[i];for(const auto& child:s.caseStatements[i])statement(*child);}int end=(int)code.size();code[noMatch].arg=defaultIndex>=0?bodyStarts[(size_t)defaultIndex]:end;for(int j:breakPatches.back())code[j].arg=end;breakPatches.pop_back();break;}
      case Stmt::Break:{if(breakPatches.empty())throw Error("break outside switch or loop during code generation");breakPatches.back().push_back(emit(Op::Jump,-1));break;}
      case Stmt::Continue:{if(loopPatches.empty())throw Error("continue outside loop during code generation");int jump=emit(Op::Jump,-1);if(loopPatches.back().continueTarget>=0)code[jump].arg=loopPatches.back().continueTarget;else loopPatches.back().pendingContinues.push_back(jump);break;}
    }
  }
  void compileFunction(const Function& fn,IRProgram& out){
    code.clear();slots.clear();next=0;
    int32_t owner=-1;
    if(!fn.ownerClass.empty()){owner=classIds.at(fn.ownerClass);slots["this"]=next++;}
    for(const auto& param:fn.params){slots[param.name]=next++;}
    if(fn.isConstructor&&!fn.ownerClass.empty()){const std::string& base=baseClasses.at(fn.ownerClass);if(!base.empty()){emit(Op::Load,slots.at("this"));emit(Op::Call,functionIds.at(base+".$ctor"));}}
    statement(*fn.body);
    if(fn.name=="main"){if(code.empty()||code.back().op!=Op::Halt)emit(Op::Halt);}
    else if(fn.returnType==Type::Void){if(code.empty()||code.back().op!=Op::Ret)emit(Op::Ret);}
    FunctionIR item;item.name=fn.ownerClass.empty()?(fn.symbol.empty()?fn.name:fn.symbol):fn.ownerClass+"."+fn.name;item.ownerClass=fn.ownerClass;item.ownerClassId=owner;item.returnType=fn.returnType;item.parameterCount=(uint32_t)fn.params.size();item.localCount=(uint32_t)next;item.code=code;out.functions.push_back(std::move(item));
  }
public:
  IRProgram generate(const Program& p){
    IRProgram out;
    for(size_t i=0;i<p.classes.size();++i)classIds[p.classes[i].name]=(int)i;
    for(size_t i=0;i<p.classes.size();++i){
      const auto& decl=p.classes[i];ClassIR c;c.name=decl.name;baseClasses[decl.name]=decl.baseClass;
      if(!decl.baseClass.empty()){
        int base=classIds.at(decl.baseClass);if(base>=(int)i)throw Error("Base class must appear before derived class");
        c.baseClassId=base;c.fieldTypes=out.classes[(size_t)base].fieldTypes;fieldSlots[decl.name]=fieldSlots[decl.baseClass];
      }
      for(const auto& field:decl.fields){fieldSlots[decl.name][field.name]=(int)c.fieldTypes.size();c.fieldTypes.push_back(field.type);}
      out.classes.push_back(std::move(c));
    }
    int id=0;for(const auto& fn:p.functions)functionIds[fn.symbol.empty()?fn.name:fn.symbol]=id++;
    for(const auto& cls:p.classes)for(const auto& method:cls.methods)functionIds[cls.name+"."+method.name]=id++;
    for(const auto& fn:p.functions)compileFunction(fn,out);
    for(const auto& cls:p.classes)for(const auto& method:cls.methods)compileFunction(method,out);
    return out;
  }
};struct Bytecode { Op op; int64_t arg=0; std::string text; };
struct ClassBC { std::string name; int32_t baseClassId=-1; std::vector<Type> fieldTypes; };
struct FunctionBC { std::string name; int32_t ownerClassId=-1; Type returnType=Type::Void; uint32_t parameterCount=0; uint32_t localCount=0; std::vector<Bytecode> code; };
struct BytecodeProgram { std::vector<ClassBC> classes; std::vector<FunctionBC> functions; };
class BytecodeLowerer {
public:
  BytecodeProgram lower(const IRProgram& ir){
    BytecodeProgram out;
    for(const auto& cls:ir.classes)out.classes.push_back({cls.name,cls.baseClassId,cls.fieldTypes});
    for(const auto& fn:ir.functions){
      FunctionBC b;b.name=fn.name;b.ownerClassId=fn.ownerClassId;b.returnType=fn.returnType;b.parameterCount=fn.parameterCount;b.localCount=fn.localCount;
      for(const auto& i:fn.code)b.code.push_back({i.op,i.arg,i.text});
      out.functions.push_back(std::move(b));
    }
    return out;
  }
};
static void writeU32(std::ofstream& f,uint32_t x){for(int i=0;i<4;++i)f.put((char)(x>>(8*i)));}
static uint32_t readU32(std::ifstream& f){uint32_t x=0;for(int i=0;i<4;++i){int c=f.get();if(c==EOF)throw Error("Truncated .room file");x|=(uint32_t)(uint8_t)c<<(8*i);}return x;}
static void writeU64(std::ofstream& f,uint64_t x){for(int i=0;i<8;++i)f.put((char)(x>>(8*i)));}
static uint64_t readU64(std::ifstream& f){uint64_t x=0;for(int i=0;i<8;++i){int c=f.get();if(c==EOF)throw Error("Truncated .room file");x|=(uint64_t)(uint8_t)c<<(8*i);}return x;}
static void writeString(std::ofstream& f,const std::string& value){if(value.size()>1000000)throw Error("String too long for .room file");writeU32(f,(uint32_t)value.size());f.write(value.data(),(std::streamsize)value.size());}
static std::string readString(std::ifstream& f){uint32_t n=readU32(f);if(n>1000000)throw Error("String length limit exceeded");std::string value(n,'\0');if(n){f.read(value.data(),n);if((uint32_t)f.gcount()!=n)throw Error("Truncated string in .room file");}return value;}
static void saveRoom(const std::string& path,const BytecodeProgram& program){
  std::ofstream f(path,std::ios::binary);if(!f)throw Error("Cannot create output file");
  f.write("ALRM",4);writeU32(f,7);writeU32(f,(uint32_t)program.classes.size());
  for(const auto& cls:program.classes){writeString(f,cls.name);writeU32(f,(uint32_t)cls.baseClassId);writeU32(f,(uint32_t)cls.fieldTypes.size());for(Type type:cls.fieldTypes)writeU32(f,(uint32_t)type);}
  writeU32(f,(uint32_t)program.functions.size());
  for(const auto& fn:program.functions){
    writeString(f,fn.name);writeU32(f,(uint32_t)fn.ownerClassId);writeU32(f,(uint32_t)fn.returnType);writeU32(f,fn.parameterCount);writeU32(f,fn.localCount);writeU32(f,(uint32_t)fn.code.size());
    for(const auto& i:fn.code){f.put((char)i.op);writeU64(f,(uint64_t)i.arg);writeString(f,i.text);}
  }
  if(!f)throw Error("Failed writing .room file");
}
static BytecodeProgram loadRoom(const std::string& path){
  std::ifstream f(path,std::ios::binary);if(!f)throw Error("Cannot open .room file");
  char magic[4];f.read(magic,4);if(f.gcount()!=4||std::string(magic,4)!="ALRM")throw Error("Invalid .room magic");
  if(readU32(f)!=7)throw Error("Unsupported .room version (expected 7)");
  BytecodeProgram program;uint32_t classCount=readU32(f);if(classCount>10000)throw Error("Class count limit exceeded");
  for(uint32_t i=0;i<classCount;++i){ClassBC cls;cls.name=readString(f);cls.baseClassId=(int32_t)readU32(f);if(cls.baseClassId < -1 || (cls.baseClassId>=0&&(uint32_t)cls.baseClassId>=i))throw Error("Invalid or forward class inheritance reference");uint32_t fields=readU32(f);if(fields>100000)throw Error("Class field count limit exceeded");for(uint32_t j=0;j<fields;++j){uint32_t type=readU32(f);if(type>(uint32_t)Type::Bool)throw Error("Only scalar class fields are supported in .room v6");cls.fieldTypes.push_back((Type)type);}program.classes.push_back(std::move(cls));}
  uint32_t n=readU32(f);if(n==0||n>10000)throw Error("Invalid function count");program.functions.reserve(n);
  for(uint32_t j=0;j<n;++j){
    FunctionBC fn;fn.name=readString(f);fn.ownerClassId=(int32_t)readU32(f);uint32_t ty=readU32(f);if(ty>100000||(ty>=100&&ty-100>=classCount)||(ty>(uint32_t)Type::Unknown&&ty<100))throw Error("Invalid function return type");fn.returnType=(Type)ty;
    fn.parameterCount=readU32(f);fn.localCount=readU32(f);if(fn.parameterCount>fn.localCount||fn.localCount>100000)throw Error("Invalid function local/parameter count");
    uint32_t count=readU32(f);if(count==0||count>1000000)throw Error("Invalid instruction count");fn.code.reserve(count);
    for(uint32_t k=0;k<count;++k){int op=f.get();if(op<1||op>(int)Op::Dup)throw Error("Invalid opcode");int64_t arg=(int64_t)readU64(f);std::string text=readString(f);fn.code.push_back({(Op)op,arg,std::move(text)});}
    program.functions.push_back(std::move(fn));
  }
  if(f.peek()!=EOF)throw Error("Unexpected trailing bytes in .room file");
  return program;
}
static int stackDelta(Op op){
  switch(op){
    case Op::PushInt:case Op::PushString:case Op::PushBool:case Op::Load:case Op::NewObject:return 1;
    case Op::Store:case Op::JumpFalse:case Op::Print:case Op::Pop:return -1;
    case Op::Add:case Op::Sub:case Op::Mul:case Op::Div:case Op::Mod:case Op::Eq:case Op::Ne:case Op::Lt:case Op::Gt:case Op::Le:case Op::Ge:case Op::And:case Op::Or:case Op::ArrayGet:return -1;
    case Op::ArraySet:return -3;
    case Op::SetField:return -2;
    case Op::Dup:return 1;
    default:return 0;
  }
}
static void verify(const BytecodeProgram& program){
  const auto& functions=program.functions;
  if(functions.empty()||functions[0].name!="main"||functions[0].ownerClassId!=-1)throw Error(".room must start with global main function");
  if(functions[0].returnType!=Type::Void||functions[0].parameterCount!=0)throw Error("Invalid main function signature");
  std::map<std::string,bool> names;
  for(size_t ci=0;ci<program.classes.size();++ci){const auto& cls=program.classes[ci];if(cls.name.empty()||names.count("class:"+cls.name))throw Error("Duplicate or empty class name");names["class:"+cls.name]=true;if(cls.baseClassId < -1 || (cls.baseClassId>=0&&(uint32_t)cls.baseClassId>=ci))throw Error("Invalid class inheritance graph");}
  for(const auto& fn:functions){if(fn.name.empty()||names.count("function:"+fn.name))throw Error("Empty or duplicate function name");names["function:"+fn.name]=true;if(fn.ownerClassId < -1 || (fn.ownerClassId>=0&&(uint32_t)fn.ownerClassId>=program.classes.size()))throw Error("Invalid method owner class");}
  for(size_t fi=0;fi<functions.size();++fi){
    const auto& fn=functions[fi];if(fn.code.empty())throw Error("Empty function body: "+fn.name);
    if(fn.ownerClassId>=0&&fn.localCount<fn.parameterCount+1)throw Error("Method does not have room for its this parameter");
    std::vector<int> depth(fn.code.size(),-1);std::queue<size_t> q;depth[0]=0;q.push(0);
    while(!q.empty()){
      size_t pc=q.front();q.pop();const auto& i=fn.code[pc];
      if((i.op==Op::Load||i.op==Op::Store)&&(i.arg<0||(uint64_t)i.arg>=fn.localCount))throw Error("Invalid local slot");
      if(i.op==Op::NewObject&&(i.arg<0||(uint64_t)i.arg>=program.classes.size()))throw Error("Invalid class allocation target");
      if((i.op==Op::GetField||i.op==Op::SetField)&&(i.arg<0||i.arg>100000))throw Error("Invalid field index");
      int before=depth[pc],need=0,delta=stackDelta(i.op);
      switch(i.op){
        case Op::Store:case Op::JumpFalse:case Op::Print:case Op::Pop:case Op::ArrayLength:case Op::GetField:case Op::Dup:need=1;break;
        case Op::Add:case Op::Sub:case Op::Mul:case Op::Div:case Op::Mod:case Op::Eq:case Op::Ne:case Op::Lt:case Op::Gt:case Op::Le:case Op::Ge:case Op::And:case Op::Or:case Op::ArrayGet:case Op::SetField:need=2;break;
        case Op::ArraySet:need=3;break;
        case Op::NewArray:if(i.arg<0||i.arg>100000)throw Error("Invalid array literal size");need=(int)i.arg;delta=1-(int)i.arg;break;
        case Op::Call:{
          if(i.arg<=0||(uint64_t)i.arg>=functions.size())throw Error("Invalid call target");
          const auto& target=functions[(size_t)i.arg];need=(int)target.parameterCount+(target.ownerClassId>=0?1:0);delta=(target.returnType==Type::Void?0:1)-need;break;
        }
        case Op::Ret:need=fn.returnType==Type::Void?0:1;break;
        default:break;
      }
      if(before<need)throw Error("Bytecode stack underflow at "+fn.name+" instruction "+std::to_string(pc));
      if(i.op==Op::Ret){if(before!=need)throw Error("Invalid stack at RET in function "+fn.name);continue;}
      if(i.op==Op::Halt){if(fn.name!="main"||before!=0)throw Error("Invalid HALT in bytecode");continue;}
      int after=before+delta;
      auto visit=[&](int64_t dest){if(dest<0||(uint64_t)dest>=fn.code.size())throw Error("Invalid jump target");size_t d=(size_t)dest;if(depth[d]==-1){depth[d]=after;q.push(d);}else if(depth[d]!=after)throw Error("Inconsistent stack depth at control-flow join");};
      if(i.op==Op::Jump)visit(i.arg);
      else if(i.op==Op::JumpFalse){visit(i.arg);if(pc+1<fn.code.size())visit((int64_t)pc+1);}
      else if(i.op!=Op::Halt){if(pc+1>=fn.code.size())throw Error("Execution can fall off end of function "+fn.name);visit((int64_t)pc+1);}
    }
  }
}
struct Array;
struct Object;
using ArrayPtr=std::shared_ptr<Array>;
using ObjectPtr=std::shared_ptr<Object>;
using Value=std::variant<int64_t,std::string,bool,ArrayPtr,ObjectPtr>;
struct Array { std::vector<Value> items; size_t elementKind=std::numeric_limits<size_t>::max(); };
struct Object { size_t classId=0; std::vector<Value> fields; };
static bool isInstanceOf(size_t actual,size_t expected,const std::vector<ClassBC>& classes){while(actual<classes.size()){if(actual==expected)return true;int32_t parent=classes[actual].baseClassId;if(parent<0)return false;actual=(size_t)parent;}return false;}
static std::string show(const Value& v){if(auto x=std::get_if<int64_t>(&v))return std::to_string(*x);if(auto x=std::get_if<std::string>(&v))return *x;if(auto x=std::get_if<bool>(&v))return *x?"true":"false";if(auto a=std::get_if<ArrayPtr>(&v))return "[array length="+std::to_string((*a)->items.size())+"]";return "[object]";}
static int64_t integer(const Value& v){if(auto x=std::get_if<int64_t>(&v))return *x;throw Error("Runtime type error: expected int");}
static bool boolean(const Value& v){if(auto x=std::get_if<bool>(&v))return *x;throw Error("Runtime type error: expected boolean");}
static int64_t checkedAdd(int64_t a,int64_t b){int64_t r;if(__builtin_add_overflow(a,b,&r))throw Error("Integer overflow");return r;}
static int64_t checkedSub(int64_t a,int64_t b){int64_t r;if(__builtin_sub_overflow(a,b,&r))throw Error("Integer overflow");return r;}
static int64_t checkedMul(int64_t a,int64_t b){int64_t r;if(__builtin_mul_overflow(a,b,&r))throw Error("Integer overflow");return r;}
static void execute(const BytecodeProgram& program){
  verify(program);const auto& functions=program.functions;const auto& classes=program.classes;
  struct Frame{size_t function=0,pc=0;std::vector<std::optional<Value>> locals;size_t stackBase=0;};
  std::vector<Value> stack;
  auto pop=[&](){if(stack.empty())throw Error("Runtime stack underflow");Value v=std::move(stack.back());stack.pop_back();return v;};
  Frame entry;entry.function=0;entry.locals.resize(functions[0].localCount);std::vector<Frame> frames;frames.push_back(std::move(entry));
  while(!frames.empty()){
    Frame& frame=frames.back();const auto& fn=functions[frame.function];if(frame.pc>=fn.code.size())throw Error("Instruction pointer outside function");const auto& i=fn.code[frame.pc++];
    switch(i.op){
      case Op::Dup:if(stack.empty())throw Error("DUP on empty stack");stack.push_back(stack.back());break;
      case Op::PushInt:stack.emplace_back(i.arg);break;
      case Op::PushString:stack.emplace_back(i.text);break;
      case Op::PushBool:stack.emplace_back(i.arg!=0);break;
      case Op::Load:{if(i.arg<0||(uint64_t)i.arg>=frame.locals.size()||!frame.locals[(size_t)i.arg])throw Error("Read of uninitialized local variable");stack.push_back(*frame.locals[(size_t)i.arg]);break;}
      case Op::Store:{if(i.arg<0||(uint64_t)i.arg>=frame.locals.size())throw Error("Invalid local slot");frame.locals[(size_t)i.arg]=pop();break;}
      case Op::Print:std::cout<<show(pop())<<'\n';break;
      case Op::Pop:pop();break;
      case Op::Neg:{auto a=integer(pop());if(a==std::numeric_limits<int64_t>::min())throw Error("Integer overflow");stack.emplace_back(-a);break;}
      case Op::Not:stack.emplace_back(!boolean(pop()));break;
      case Op::Add:{Value b=pop(),a=pop();if(std::holds_alternative<std::string>(a)&&std::holds_alternative<std::string>(b))stack.emplace_back(std::get<std::string>(a)+std::get<std::string>(b));else stack.emplace_back(checkedAdd(integer(a),integer(b)));break;}
      case Op::Sub:{auto b=integer(pop()),a=integer(pop());stack.emplace_back(checkedSub(a,b));break;}
      case Op::Mul:{auto b=integer(pop()),a=integer(pop());stack.emplace_back(checkedMul(a,b));break;}
      case Op::Div:case Op::Mod:{auto b=integer(pop()),a=integer(pop());if(b==0)throw Error("Division by zero");if(a==std::numeric_limits<int64_t>::min()&&b==-1)throw Error("Integer overflow");stack.emplace_back(i.op==Op::Div?a/b:a%b);break;}
      case Op::Eq:case Op::Ne:{Value b=pop(),a=pop();bool same=a.index()==b.index()&&a==b;stack.emplace_back(i.op==Op::Eq?same:!same);break;}
      case Op::Lt:case Op::Gt:case Op::Le:case Op::Ge:{auto b=integer(pop()),a=integer(pop());bool r=i.op==Op::Lt?a<b:i.op==Op::Gt?a>b:i.op==Op::Le?a<=b:a>=b;stack.emplace_back(r);break;}
      case Op::And:case Op::Or:{bool b=boolean(pop()),a=boolean(pop());stack.emplace_back(i.op==Op::And?(a&&b):(a||b));break;}
      case Op::NewArray:{if(i.arg<0||(uint64_t)i.arg>stack.size())throw Error("Invalid array construction");auto a=std::make_shared<Array>();a->items.resize((size_t)i.arg);for(size_t k=(size_t)i.arg;k>0;--k)a->items[k-1]=pop();if(!a->items.empty()){a->elementKind=a->items[0].index();for(const auto& v:a->items)if(v.index()!=a->elementKind)throw Error("Array element type mismatch in bytecode");}stack.emplace_back(a);break;}
      case Op::ArrayGet:{auto index=integer(pop());Value base=pop();auto a=std::get_if<ArrayPtr>(&base);if(!a||!*a)throw Error("Array access on non-array value");if(index<0||(uint64_t)index>=(*a)->items.size())throw Error("Array index out of bounds");stack.push_back((*a)->items[(size_t)index]);break;}
      case Op::ArraySet:{Value value=pop();auto index=integer(pop());Value base=pop();auto a=std::get_if<ArrayPtr>(&base);if(!a||!*a)throw Error("Array assignment on non-array value");if(index<0||(uint64_t)index>=(*a)->items.size())throw Error("Array index out of bounds");if((*a)->elementKind==std::numeric_limits<size_t>::max())(*a)->elementKind=value.index();if(value.index()!=(*a)->elementKind)throw Error("Array element type mismatch");(*a)->items[(size_t)index]=std::move(value);break;}
      case Op::ArrayLength:{Value base=pop();auto a=std::get_if<ArrayPtr>(&base);if(!a||!*a)throw Error(".length on non-array value");stack.emplace_back((int64_t)(*a)->items.size());break;}
      case Op::NewObject:{if(i.arg<0||(uint64_t)i.arg>=classes.size())throw Error("Invalid class allocation");auto obj=std::make_shared<Object>();obj->classId=(size_t)i.arg;for(Type type:classes[obj->classId].fieldTypes){if(type==Type::Int)obj->fields.emplace_back((int64_t)0);else if(type==Type::String)obj->fields.emplace_back(std::string());else if(type==Type::Bool)obj->fields.emplace_back(false);else throw Error("Unsupported object field type");}stack.emplace_back(obj);break;}
      case Op::GetField:{Value base=pop();auto obj=std::get_if<ObjectPtr>(&base);if(!obj||!*obj)throw Error("Field read on non-object value");if(i.arg<0||(uint64_t)i.arg>=(*obj)->fields.size())throw Error("Object field index out of bounds");stack.push_back((*obj)->fields[(size_t)i.arg]);break;}
      case Op::SetField:{Value value=pop(),base=pop();auto obj=std::get_if<ObjectPtr>(&base);if(!obj||!*obj)throw Error("Field write on non-object value");if(i.arg<0||(uint64_t)i.arg>=(*obj)->fields.size())throw Error("Object field index out of bounds");Type expected=classes[(*obj)->classId].fieldTypes[(size_t)i.arg];size_t actual=value.index();size_t expectedIndex=expected==Type::Int?0:expected==Type::String?1:2;if(actual!=expectedIndex)throw Error("Object field type mismatch");(*obj)->fields[(size_t)i.arg]=std::move(value);break;}
      case Op::Call:{
        size_t target=(size_t)i.arg;const auto& callee=functions[target];if(frames.size()>=1024)throw Error("Maximum call depth exceeded");
        size_t count=(size_t)callee.parameterCount+(callee.ownerClassId>=0?1:0);std::vector<Value> args(count);for(size_t k=count;k>0;--k)args[k-1]=pop();
        if(callee.ownerClassId>=0){auto obj=std::get_if<ObjectPtr>(&args[0]);if(!obj||!*obj||!isInstanceOf((*obj)->classId,(size_t)callee.ownerClassId,classes))throw Error("Instance method called with incompatible receiver object");}
        Frame next;next.function=target;next.locals.resize(callee.localCount);next.stackBase=stack.size();for(size_t k=0;k<args.size();++k)next.locals[k]=std::move(args[k]);frames.push_back(std::move(next));break;
      }
      case Op::Ret:{bool returns=fn.returnType!=Type::Void;Value result;if(returns)result=pop();size_t base=frame.stackBase;frames.pop_back();if(frames.empty())return;if(stack.size()<base)throw Error("Call frame stack underflow");stack.resize(base);if(returns)stack.push_back(std::move(result));break;}
      case Op::Jump:frame.pc=(size_t)i.arg;break;
      case Op::JumpFalse:{bool cond=boolean(pop());if(!cond)frame.pc=(size_t)i.arg;break;}
      case Op::Halt:return;
    }
  }
}
} // namespace al

int main(int argc,char** argv){try{if(argc<3){std::cerr<<"Usage: alvm compile <file.al> [-o file.room] | run <file.room> | disasm <file.room>\n";return 2;}std::string command=argv[1],input=argv[2];if(command=="compile"){std::ifstream f(input);if(!f)throw al::Error("Cannot open source file");std::string source((std::istreambuf_iterator<char>(f)),{});al::Program program=al::Parser(source).parse();al::SemanticAnalyzer().analyze(program);auto ir=al::IRGenerator().generate(program);auto bc=al::BytecodeLowerer().lower(ir);std::string output=input;auto dot=output.find_last_of('.');if(dot!=std::string::npos)output.resize(dot);output+=".room";for(int i=3;i+1<argc;++i)if(std::string(argv[i])=="-o")output=argv[i+1];al::saveRoom(output,bc);std::cout<<"Compiled "<<output<<" ("<<bc.functions.size()<<" functions, "<<bc.classes.size()<<" classes)\n";}else if(command=="run"){al::execute(al::loadRoom(input));}else if(command=="disasm"){auto bc=al::loadRoom(input);al::verify(bc);for(const auto& cls:bc.classes)std::cout<<"class "<<cls.name<<" fields="<<cls.fieldTypes.size()<<'\n';for(const auto& fn:bc.functions){std::cout<<"function "<<fn.name<<" locals="<<fn.localCount<<" params="<<fn.parameterCount<<'\n';for(size_t i=0;i<fn.code.size();++i)std::cout<<i<<": op="<<(int)fn.code[i].op<<" arg="<<fn.code[i].arg<<(fn.code[i].text.empty()?"":" text=\""+fn.code[i].text+"\"")<<'\n';}}else throw al::Error("Unknown command: "+command);return 0;}catch(const std::exception& e){std::cerr<<"AL error: "<<e.what()<<'\n';return 1;}}
