public class LSMCreator {
    public static void main(String[] args) throws IOException {
        LSMTree tree = new LSMTree("data", 3);

        tree.put("apple", "fruit");
        tree.put("aardvark", "animal");
        tree.put("banana", "yellow"); // triggers flush
        tree.put("apple", "tech");     // new
        tree.put("cherry", "red");

        System.out.println("apple => " + tree.get("apple"));
        System.out.println("banana => " + tree.get("banana"));
        System.out.println("aardvark => " + tree.get("aardvark"));
        System.out.println("cherry => " + tree.get("cherry"));
    }
}
