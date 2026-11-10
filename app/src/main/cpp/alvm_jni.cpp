#include <jni.h>
#include <iostream>
#include <sstream>
#include <string>

#define main alvm_cli_main
#include "../../../../src/alvm.cpp"
#undef main

static std::string fromJava(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

static jstring toJava(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_alvmstudio_MainActivity_nativeCompile(
        JNIEnv* env, jclass, jstring sourceValue, jstring filesDirValue) {
    const std::string source = fromJava(env, sourceValue);
    const std::string filesDir = fromJava(env, filesDirValue);
    try {
        al::Program program = al::Parser(source).parse();
        al::SemanticAnalyzer().analyze(program);
        auto ir = al::IRGenerator().generate(program);
        auto bytecode = al::BytecodeLowerer().lower(ir);
        const std::string outputPath = filesDir + "/main.room";
        al::saveRoom(outputPath, bytecode);
        return toJava(env, "Compiled main.room (" +
            std::to_string(bytecode.functions.size()) + " functions, " +
            std::to_string(bytecode.classes.size()) + " classes).");
    } catch (const std::exception& error) {
        return toJava(env, std::string("AL compile error: ") + error.what());
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_alvmstudio_MainActivity_nativeRun(
        JNIEnv* env, jclass, jstring roomPathValue) {
    const std::string roomPath = fromJava(env, roomPathValue);
    std::ostringstream captured;
    std::streambuf* previous = std::cout.rdbuf(captured.rdbuf());
    try {
        al::execute(al::loadRoom(roomPath));
    } catch (const std::exception& error) {
        captured << "AL runtime error: " << error.what() << '\n';
    }
    std::cout.rdbuf(previous);
    return toJava(env, captured.str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_alvmstudio_MainActivity_nativeGetUiJson(JNIEnv* env, jclass) {
    return toJava(env, al::uiJson());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_alvmstudio_MainActivity_nativeRunUiEvent(
        JNIEnv* env, jclass, jstring roomPathValue, jstring callbackValue, jstring eventValue) {
    const std::string roomPath = fromJava(env, roomPathValue);
    const std::string callback = fromJava(env, callbackValue);
    const std::string event = fromJava(env, eventValue);
    std::ostringstream captured;
    std::streambuf* previous = std::cout.rdbuf(captured.rdbuf());
    try { al::execute(al::loadRoom(roomPath), callback, event); }
    catch (const std::exception& error) { captured << "AL UI event error: " << error.what() << '\n'; }
    std::cout.rdbuf(previous);
    return toJava(env, captured.str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_alvmstudio_MainActivity_nativeDisassemble(
        JNIEnv* env, jclass, jstring roomPathValue) {
    const std::string roomPath = fromJava(env, roomPathValue);
    std::ostringstream captured;
    try {
        auto bytecode = al::loadRoom(roomPath);
        al::verify(bytecode);
        for (const auto& cls : bytecode.classes) {
            captured << "class " << cls.name
                     << " fields=" << cls.fieldTypes.size() << '\n';
        }
        for (const auto& function : bytecode.functions) {
            captured << "function " << function.name
                     << " locals=" << function.localCount
                     << " params=" << function.parameterCount << '\n';
            for (size_t index = 0; index < function.code.size(); ++index) {
                const auto& instruction = function.code[index];
                captured << index << ": op=" << static_cast<int>(instruction.op)
                         << " arg=" << instruction.arg;
                if (!instruction.text.empty()) {
                    captured << " text=\"" << instruction.text << "\"";
                }
                captured << '\n';
            }
        }
    } catch (const std::exception& error) {
        captured << "AL bytecode error: " << error.what() << '\n';
    }
    return toJava(env, captured.str());
}


// Rust CI Runtime host ABI. The C++ file remains the Android/JNI boundary;
// all bytecode execution and runtime package management is implemented in Rust.
extern "C" {
    char* ci_runtime_host_run(
        const char* room_path,
        const char* runtime_root,
        const char* abi,
        const char* entry_name,
        const char* event_value);
    char* ci_runtime_host_status(const char* runtime_root, const char* abi);
    char* ci_runtime_host_install_update(
        const char* package_path,
        const char* runtime_root,
        const char* abi);
    char* ci_runtime_host_rollback(const char* runtime_root, const char* abi);
    void ci_runtime_free_string(char* value);
}

static jstring rustJsonResult(JNIEnv* env, char* value) {
    if (value == nullptr) {
        return toJava(env, "{\"ok\":false,\"error\":\"Rust CI Runtime returned a null result\"}");
    }
    std::string result(value);
    ci_runtime_free_string(value);
    return toJava(env, result);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_alvmstudio_CiRuntimeHost_nativeRunRoomJson(
        JNIEnv* env, jclass,
        jstring roomPathValue,
        jstring runtimeRootValue,
        jstring abiValue,
        jstring entryNameValue,
        jstring eventValue) {
    const std::string roomPath = fromJava(env, roomPathValue);
    const std::string runtimeRoot = fromJava(env, runtimeRootValue);
    const std::string abi = fromJava(env, abiValue);
    const std::string entryName = fromJava(env, entryNameValue);
    const std::string event = fromJava(env, eventValue);
    return rustJsonResult(env, ci_runtime_host_run(
        roomPath.c_str(), runtimeRoot.c_str(), abi.c_str(),
        entryName.c_str(), event.c_str()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_alvmstudio_CiRuntimeHost_nativeStatusJson(
        JNIEnv* env, jclass, jstring runtimeRootValue, jstring abiValue) {
    const std::string runtimeRoot = fromJava(env, runtimeRootValue);
    const std::string abi = fromJava(env, abiValue);
    return rustJsonResult(env, ci_runtime_host_status(runtimeRoot.c_str(), abi.c_str()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_alvmstudio_CiRuntimeHost_nativeInstallUpdateJson(
        JNIEnv* env, jclass,
        jstring packagePathValue, jstring runtimeRootValue, jstring abiValue) {
    const std::string packagePath = fromJava(env, packagePathValue);
    const std::string runtimeRoot = fromJava(env, runtimeRootValue);
    const std::string abi = fromJava(env, abiValue);
    return rustJsonResult(env, ci_runtime_host_install_update(
        packagePath.c_str(), runtimeRoot.c_str(), abi.c_str()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_alvmstudio_CiRuntimeHost_nativeRollbackJson(
        JNIEnv* env, jclass, jstring runtimeRootValue, jstring abiValue) {
    const std::string runtimeRoot = fromJava(env, runtimeRootValue);
    const std::string abi = fromJava(env, abiValue);
    return rustJsonResult(env, ci_runtime_host_rollback(
        runtimeRoot.c_str(), abi.c_str()));
}
