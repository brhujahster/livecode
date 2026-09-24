package co.inter.piggies.reserve.facade;

public record AccountView(String agency, String accountNumber, String ownerCpf,
                          long balance, long reservedBalance, long availableBalance) {
}
