use serde_json::json;
use std::env;
use std::process::ExitCode;

fn usage() {
    eprintln!(
        "CI Runtime (Rust)\n\nUsage:\n  ci-runtime self-test\n  ci-runtime run <file.room> [--event <handler> <value>]..."
    );
}

fn main() -> ExitCode {
    let args: Vec<String> = env::args().collect();
    if args.len() == 2 && args[1] == "self-test" {
        if ci_runtime::self_test() {
            println!("CI Runtime {}: self-test passed", ci_runtime::BUNDLED_VERSION);
            return ExitCode::SUCCESS;
        }
        eprintln!("CI Runtime self-test failed");
        return ExitCode::FAILURE;
    }

    if args.len() < 3 || args[1] != "run" {
        usage();
        return ExitCode::from(2);
    }

    let room_path = &args[2];
    let mut result = ci_runtime::run_room(room_path, "main", "");
    if result["ok"].as_bool() != Some(true) {
        eprintln!("{}", result["error"].as_str().unwrap_or("CI Runtime failed"));
        println!("{}", result);
        return ExitCode::FAILURE;
    }

    let mut outputs = vec![result["output"].as_str().unwrap_or("").to_string()];
    let mut index = 3;
    while index < args.len() {
        if args[index] != "--event" || index + 2 >= args.len() {
            usage();
            return ExitCode::from(2);
        }
        let handler = &args[index + 1];
        let event_value = &args[index + 2];
        result = ci_runtime::run_room(room_path, handler, event_value);
        if result["ok"].as_bool() != Some(true) {
            eprintln!("{}", result["error"].as_str().unwrap_or("CI Runtime event failed"));
            println!("{}", result);
            return ExitCode::FAILURE;
        }
        outputs.push(result["output"].as_str().unwrap_or("").to_string());
        index += 3;
    }

    result["output"] = json!(outputs.into_iter().filter(|value| !value.is_empty()).collect::<Vec<_>>().join("\n"));
    println!("{}", serde_json::to_string(&result).unwrap_or_else(|_| json!({"ok":false,"error":"Unable to encode runtime result"}).to_string()));
    ExitCode::SUCCESS
}
