async function bar() {
	let a = await foo();
	return a + 2
}

bar();
