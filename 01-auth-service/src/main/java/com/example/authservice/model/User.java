package com.example.authservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

// @Entity tells jpa/hibernate this class maps to a database table, @Table pins the exact table name
// lombok's @Getter/@Setter generate all the boilerplate getter and setter methods at compile time,
// so they never actually show up in this source file even though other classes call them
// implementing userdetails is what lets spring security treat this entity as the logged in principal
@Entity
@Table(name = "users")
@Getter
@Setter
public class User implements UserDetails {

    // generationtype.identity means the database itself assigns the id on insert (like postgres
    // serial/identity columns), as opposed to sequence or table strategies which ask the db for
    // the next id value up front before the row is even inserted
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    private String phoneNumber;

    private Boolean totpEnabled = false;
    private String totpSecret;

    // these five overrides come from the userdetails interface, spring security calls them to
    // decide whether a login attempt is even allowed to succeed, all hardcoded true/empty here
    // since this project is not using account lockout or role based authorities yet
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}